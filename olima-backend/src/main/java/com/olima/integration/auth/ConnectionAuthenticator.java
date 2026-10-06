package com.olima.integration.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.olima.common.http.OutboundRequestException;
import com.olima.common.http.Request;
import com.olima.common.http.Response;
import com.olima.common.http.SafeHttpClient;
import com.olima.integration.config.AuthSettings;
import com.olima.integration.config.IntegrationProperties;
import com.olima.integration.dto.ConnectionSnapshot;
import com.olima.integration.enums.AuthType;
import com.olima.integration.exception.IntegrationAuthException;
import com.olima.integration.token.CachedToken;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class ConnectionAuthenticator {

  private static final DataSize MAX_TOKEN_RESPONSE = DataSize.ofKilobytes(64);
  private static final Duration MIN_TTL = Duration.ofSeconds(10);

  private final SafeHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final IntegrationProperties properties;
  private final MeterRegistry meterRegistry;
  private final Cache<UUID, CachedToken> tokens;

  public ConnectionAuthenticator(
      SafeHttpClient httpClient,
      ObjectMapper objectMapper,
      IntegrationProperties properties,
      MeterRegistry meterRegistry) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.tokens =
        Caffeine.newBuilder()
            .maximumSize(properties.maxCachedTokens())
            .expireAfter(
                new Expiry<UUID, CachedToken>() {
                  @Override
                  public long expireAfterCreate(UUID key, CachedToken value, long currentTime) {
                    return value.ttl().toNanos();
                  }

                  @Override
                  public long expireAfterUpdate(
                      UUID key, CachedToken value, long currentTime, long currentDuration) {
                    return value.ttl().toNanos();
                  }

                  @Override
                  public long expireAfterRead(
                      UUID key, CachedToken value, long currentTime, long currentDuration) {
                    return currentDuration;
                  }
                })
            .build();
  }

  public Map<String, String> authHeaders(ConnectionSnapshot connection) {
    AuthSettings s = connection.settings();
    return switch (connection.authType()) {
      case NONE -> Map.of();
      case BASIC ->
          Map.of(HttpHeaders.AUTHORIZATION, basic(connection.username(), connection.password()));
      case BEARER, API_KEY -> Map.of(s.headerName(), s.headerPrefix() + connection.secret());
      case LOGIN, OAUTH2_CLIENT_CREDENTIALS ->
          Map.of(s.headerName(), s.headerPrefix() + token(connection));
    };
  }

  public void invalidate(UUID connectionId) {
    tokens.invalidate(connectionId);
  }

  public String resolve(ConnectionSnapshot connection, String pathOrUrl) {
    if (pathOrUrl == null || pathOrUrl.isBlank()) {
      return connection.baseUri().toString();
    }
    String value = pathOrUrl.trim();
    if (value.regionMatches(true, 0, "http://", 0, 7)
        || value.regionMatches(true, 0, "https://", 0, 8)) {
      URI target = URI.create(value.replaceAll("\\{[^}]*}", "x"));
      if (!sameOrigin(connection.baseUri(), target)) {
        throw new OutboundRequestException(
            "Endpoint host must match connection '"
                + connection.name()
                + "' ("
                + connection.baseUri().getHost()
                + ")");
      }
      return value;
    }
    String base = connection.baseUri().toString().replaceAll("/+$", "");
    return base + (value.startsWith("/") ? value : "/" + value);
  }

  public void verify(ConnectionSnapshot connection) {
    if (connection.authType().issuesTokens()) {
      invalidate(connection.id());
      token(connection);
    }
  }

  private String token(ConnectionSnapshot connection) {
    return tokens.get(connection.id(), id -> fetchToken(connection)).value();
  }

  private CachedToken fetchToken(ConnectionSnapshot c) {
    AuthSettings s = c.settings();
    if (s.tokenPath() == null) {
      throw new IntegrationAuthException(
          "Connection '" + c.name() + "' has no token endpoint (authConfig.tokenPath)");
    }
    URI tokenUri = URI.create(resolve(c, s.tokenPath()));

    Map<String, String> fields = new LinkedHashMap<>();
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    if (c.authType() == AuthType.OAUTH2_CLIENT_CREDENTIALS) {
      fields.put("grant_type", "client_credentials");
      if (s.scope() != null && !s.scope().isBlank()) {
        fields.put("scope", s.scope());
      }
      headers.put(HttpHeaders.AUTHORIZATION, basic(c.username(), c.password()));
    } else {
      fields.put(s.usernameField(), c.username());
      fields.put(s.passwordField(), c.password());
    }
    fields.putAll(s.extraFields());

    boolean form = AuthSettings.FORM.equals(s.bodyFormat());
    byte[] body =
        (form ? formEncode(fields) : objectMapper.writeValueAsString(fields))
            .getBytes(StandardCharsets.UTF_8);
    String contentType =
        form ? MediaType.APPLICATION_FORM_URLENCODED_VALUE : MediaType.APPLICATION_JSON_VALUE;

    Response response =
        httpClient.execute(
            Request.of(HttpMethod.POST, tokenUri)
                .withHeaders(headers)
                .withBody(body, contentType)
                .withTimeout(properties.loginTimeout())
                .withMaxSize(MAX_TOKEN_RESPONSE));

    if (!response.isSuccess()) {
      meterRegistry.counter("olima.integration.login", "result", "rejected").increment();

      throw new IntegrationAuthException(
          "Login to '" + c.name() + "' failed: HTTP " + response.status());
    }

    JsonNode json;
    try {
      json = objectMapper.readTree(response.bodyAsString());
    } catch (Exception e) {
      throw new IntegrationAuthException("Login to '" + c.name() + "' returned non-JSON response");
    }
    JsonNode tokenNode = json.at(pointer(s.tokenJsonPath()));
    if (tokenNode.isMissingNode() || tokenNode.isNull() || tokenNode.asString().isBlank()) {
      throw new IntegrationAuthException(
          "Login to '" + c.name() + "' succeeded but no token at '" + s.tokenJsonPath() + "'");
    }

    Duration ttl = Duration.ofSeconds(s.tokenTtlSeconds());
    JsonNode expiresNode = json.at(pointer(s.expiresInJsonPath()));
    if (expiresNode.isNumber() && expiresNode.asLong() > 0) {
      ttl = Duration.ofSeconds(expiresNode.asLong());
    }
    ttl = ttl.minus(properties.tokenRefreshSkew());
    if (ttl.compareTo(MIN_TTL) < 0) {
      ttl = MIN_TTL;
    }
    meterRegistry.counter("olima.integration.login", "result", "success").increment();
    log.info(
        "Obtained token for connection '{}' (organization {}), cached for {}",
        c.name(),
        c.organizationId(),
        ttl);
    return new CachedToken(tokenNode.asString(), ttl);
  }

  public static String pointer(String dotPath) {
    if (dotPath == null || dotPath.isBlank()) {
      return "/access_token";
    }
    if (dotPath.startsWith("/")) {
      return dotPath;
    }
    return "/" + dotPath.trim().replace("~", "~0").replace("/", "~1").replace('.', '/');
  }

  private static boolean sameOrigin(URI a, URI b) {
    return a.getScheme() != null
        && a.getScheme().equalsIgnoreCase(b.getScheme())
        && a.getHost() != null
        && a.getHost().equalsIgnoreCase(b.getHost())
        && effectivePort(a) == effectivePort(b);
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private static String basic(String username, String password) {
    String raw = (username == null ? "" : username) + ":" + (password == null ? "" : password);
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String formEncode(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(
                        e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
