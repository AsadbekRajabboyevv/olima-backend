package com.olima.common.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SafeHttpClient {

  private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);
  private static final Pattern CHARSET = Pattern.compile("(?i)charset=\"?([\\w.:-]+)\"?");

  private final HttpClientProperties properties;
  private final OutboundUrlPolicy policy;
  private final HttpClient client;

  public SafeHttpClient(HttpClientProperties properties, OutboundUrlPolicy policy) {
    this.properties = properties;
    this.policy = policy;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public Response get(String url, Map<String, String> headers) {
    return execute(Request.of(HttpMethod.GET, policy.requireAllowed(url)).withHeaders(headers));
  }

  public Response execute(Request request) {
    URI current = policy.requireAllowed(request.uri());
    HttpMethod method = request.method();
    byte[] body = request.body();
    Duration timeout = request.timeout() != null ? request.timeout() : properties.readTimeout();
    long maxBytes =
        (request.maxSize() != null ? request.maxSize() : properties.maxResponseSize()).toBytes();

    for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
      HttpResponse<InputStream> response =
          send(current, method, body, request.headers(), request.contentType(), timeout);
      int status = response.statusCode();
      if (!REDIRECT_CODES.contains(status)) {
        return read(response, current, maxBytes);
      }
      String location = response.headers().firstValue(HttpHeaders.LOCATION).orElse(null);
      closeQuietly(response);
      if (location == null || location.isBlank()) {
        throw new OutboundRequestException("Redirect without Location header: " + current);
      }
      current = policy.requireAllowed(current.resolve(location.trim()));

      if (status == 303 || ((status == 301 || status == 302) && method != HttpMethod.GET)) {
        method = HttpMethod.GET;
        body = null;
      }
    }
    throw new OutboundRequestException("Too many redirects: " + request.uri());
  }

  private HttpResponse<InputStream> send(
      URI uri,
      HttpMethod method,
      byte[] body,
      Map<String, String> headers,
      String contentType,
      Duration timeout) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header(HttpHeaders.USER_AGENT, properties.userAgent());
    headers.forEach(builder::setHeader);
    if (body != null) {
      if (contentType != null) {
        builder.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
      }
      builder.method(method.name(), HttpRequest.BodyPublishers.ofByteArray(body));
    } else {
      builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
    }
    try {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw new OutboundRequestException(
          "Request to " + uri.getHost() + " failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OutboundRequestException("Request to " + uri.getHost() + " was interrupted", e);
    }
  }

  private Response read(HttpResponse<InputStream> response, URI uri, long maxBytes) {
    try (InputStream in = response.body()) {
      byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
      boolean truncated = data.length > maxBytes;
      if (truncated) {
        byte[] cut = new byte[(int) maxBytes];
        System.arraycopy(data, 0, cut, 0, cut.length);
        data = cut;
        log.debug("Response from {} truncated at {} bytes", uri.getHost(), maxBytes);
      }
      String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null);
      return new Response(response.statusCode(), contentType, data, truncated, uri);
    } catch (IOException e) {
      throw new OutboundRequestException("Failed to read response from " + uri.getHost(), e);
    }
  }

  private static void closeQuietly(HttpResponse<InputStream> response) {
    try {
      response.body().close();
    } catch (IOException ignored) {

    }
  }
}
