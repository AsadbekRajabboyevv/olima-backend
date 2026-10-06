package com.olima.execution.executor;

import com.olima.common.error.NotFoundException;
import com.olima.common.http.OutboundRequestException;
import com.olima.common.http.Request;
import com.olima.common.http.Response;
import com.olima.common.http.SafeHttpClient;
import com.olima.execution.config.RestApiToolProperties;
import com.olima.execution.config.RestToolConfig;
import com.olima.execution.dto.ToolResult;
import com.olima.integration.IntegrationConnectionService;
import com.olima.integration.auth.ConnectionAuthenticator;
import com.olima.integration.dto.ConnectionSnapshot;
import com.olima.integration.exception.IntegrationAuthException;
import com.olima.tool.ToolEntity;
import com.olima.tool.ToolParameterEntity;
import com.olima.tool.enums.ToolType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestApiToolExecutor implements ToolExecutor {

  private static final String QUERY_VAR_PREFIX = "__q_";

  private final SafeHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final RestApiToolProperties props;
  private final IntegrationConnectionService connections;
  private final ConnectionAuthenticator authenticator;

  @Override
  public ToolType supportedType() {
    return ToolType.REST_API;
  }

  @Override
  public ToolResult execute(ToolEntity tool, Map<String, Object> parameters) {
    try {
      RestToolConfig config = RestToolConfig.parse(objectMapper, tool.getConfiguration(), props);
      Map<String, Object> args = effectiveArguments(tool, parameters);

      ConnectionSnapshot connection =
          config.connection() != null
              ? connections.snapshot(tool.getOrganizationId(), config.connection())
              : null;
      String template =
          connection != null
              ? authenticator.resolve(connection, config.endpoint())
              : config.endpoint();

      URI uri = buildUri(template, config, args);
      Request request =
          Request.of(config.method(), uri)
              .withHeaders(config.headers())
              .withHeaders(Map.of(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE + ", */*"))
              .withTimeout(config.timeout())
              .withMaxSize(props.maxResponseSize());
      if (sendsBody(config.method())) {
        Map<String, Object> bodyArgs = new LinkedHashMap<>(args);
        bodyArgs.keySet().removeAll(config.pathVariables());
        request =
            request.withBody(
                objectMapper.writeValueAsString(bodyArgs).getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE);
      }

      Response response = send(request, connection);
      if (connection != null
          && response.status() == HttpStatus.UNAUTHORIZED.value()
          && connection.authType().issuesTokens()) {

        log.info(
            "Tool '{}': connection '{}' returned 401, refreshing token",
            tool.getName(),
            connection.name());
        authenticator.invalidate(connection.id());
        response = send(request, connection);
      }
      if (!response.isSuccess()) {
        log.warn("Tool '{}' endpoint returned HTTP {}", tool.getName(), response.status());
        return ToolResult.failure("Endpoint returned HTTP " + response.status());
      }
      String body = response.bodyAsString();
      if (body.isBlank() || body.trim().equals("null")) {
        return ToolResult.success(null);
      }
      boolean truncated = response.truncated() || body.length() > props.maxResponseChars();
      if (body.length() > props.maxResponseChars()) {
        body = body.substring(0, props.maxResponseChars());
      }
      return new ToolResult(
          true,
          truncated ? body + "\n... [truncated]" : body,
          List.of(),
          null,
          Map.of("truncated", truncated, "status", response.status()));
    } catch (IntegrationAuthException e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      log.warn("Tool '{}' authentication failed: {}", tool.getName(), e.getMessage());
      return ToolResult.failure("Authentication to the external system failed");
    } catch (OutboundRequestException | IllegalArgumentException | NotFoundException e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      log.warn("Tool '{}' rejected: {}", tool.getName(), e.getMessage());
      return ToolResult.failure(e.getMessage());
    } catch (Exception e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      log.warn("Tool '{}' failed: {}", tool.getName(), e.getMessage());
      return ToolResult.failure("Tool call failed");
    }
  }

  private Response send(Request request, ConnectionSnapshot connection) {
    if (connection == null) {
      return httpClient.execute(request);
    }

    return httpClient.execute(request.withHeaders(authenticator.authHeaders(connection)));
  }

  private Map<String, Object> effectiveArguments(ToolEntity tool, Map<String, Object> provided) {
    Map<String, Object> input = provided != null ? provided : Map.of();
    Map<String, Object> effective = new HashMap<>(input);

    for (ToolParameterEntity p : tool.getParameters()) {
      if (!effective.containsKey(p.getName()) && p.getDefaultValue() != null) {
        effective.put(p.getName(), parseDefaultValue(p.getDefaultValue()));
      }
    }
    return effective;
  }

  private Object parseDefaultValue(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(raw);
      if (node.isTextual()) {
        return node.asString();
      }
      if (node.isIntegralNumber()) {
        return node.asLong();
      }
      if (node.isFloatingPointNumber()) {
        return node.asDouble();
      }
      if (node.isBoolean()) {
        return node.asBoolean();
      }
      return raw;
    } catch (Exception ignored) {
      return raw;
    }
  }

  private URI buildUri(String endpoint, RestToolConfig config, Map<String, Object> args) {
    var builder = UriComponentsBuilder.fromUriString(endpoint);
    Map<String, Object> vars = new HashMap<>();

    for (String var : config.pathVariables()) {
      Object value = args.get(var);
      if (value == null) {
        throw new IllegalArgumentException("Missing required path variable: " + var);
      }
      vars.put(var, String.valueOf(value));
    }

    boolean bodyCapable = sendsBody(config.method());
    for (Map.Entry<String, Object> entry : args.entrySet()) {
      String key = entry.getKey();
      if (config.pathVariables().contains(key)) {
        continue;
      }
      if (key.startsWith(QUERY_VAR_PREFIX)) {
        String queryName = key.substring(QUERY_VAR_PREFIX.length());
        builder.queryParam(queryName, String.valueOf(entry.getValue()));
      } else if (!bodyCapable) {
        builder.queryParam(key, String.valueOf(entry.getValue()));
      }
    }

    return builder.encode().buildAndExpand(vars).toUri();
  }

  private static boolean sendsBody(HttpMethod method) {
    return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
  }
}
