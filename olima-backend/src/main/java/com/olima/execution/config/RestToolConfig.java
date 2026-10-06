package com.olima.execution.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record RestToolConfig(
    String endpoint,
    HttpMethod method,
    Map<String, String> headers,
    Duration timeout,
    Set<String> pathVariables,
    String connection) {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_\\-]+)}");

  public static RestToolConfig parse(
      ObjectMapper mapper, String json, RestApiToolProperties props) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("REST_API tool configuration is empty");
    }
    JsonNode node = mapper.readTree(json);
    if (!node.isObject()) {
      throw new IllegalArgumentException("Tool configuration must be a JSON object");
    }
    String endpoint = text(node, "endpoint");
    if (endpoint == null) {
      endpoint = text(node, "url");
    }
    if (endpoint == null) {
      throw new IllegalArgumentException("\"endpoint\" is required for REST_API tools");
    }

    String connection = text(node, "connection");
    boolean relative =
        !endpoint.regionMatches(true, 0, "http://", 0, 7)
            && !endpoint.regionMatches(true, 0, "https://", 0, 8);
    if (relative) {
      if (connection == null) {
        throw new IllegalArgumentException(
            "\"endpoint\" must be an absolute http(s) URL (or set \"connection\")");
      }
      if (!endpoint.startsWith("/") || endpoint.startsWith("//") || endpoint.contains("://")) {
        throw new IllegalArgumentException(
            "Relative \"endpoint\" must be a path starting with '/', e.g. /students/{id}");
      }
    } else {
      var components = UriComponentsBuilder.fromUriString(endpoint).build();
      String scheme = components.getScheme();
      String host = components.getHost();
      if (scheme == null || host == null || host.isBlank()) {
        throw new IllegalArgumentException("\"endpoint\" must be an absolute http(s) URL");
      }
      if (host.contains("{") || scheme.contains("{") || (components.getUserInfo() != null)) {
        throw new IllegalArgumentException("Placeholders are not allowed in the endpoint host");
      }
    }

    String methodName = text(node, "method");
    String normalized = methodName == null ? "GET" : methodName.toUpperCase(Locale.ROOT);
    if (!props.allowedMethods().contains(normalized)) {
      throw new IllegalArgumentException("HTTP method is not allowed: " + normalized);
    }

    Map<String, String> headers = new LinkedHashMap<>();
    JsonNode headersNode = node.path("headers");
    if (headersNode.isObject()) {
      for (var entry : headersNode.properties()) {
        String name = entry.getKey().trim();
        if (props.forbiddenHeaders().contains(name.toLowerCase(Locale.ROOT))) {
          throw new IllegalArgumentException("Header is not allowed: " + name);
        }
        if (name.isEmpty() || name.contains("\n") || name.contains("\r")) {
          throw new IllegalArgumentException("Invalid header name");
        }
        headers.put(name, entry.getValue().asString());
      }
    }

    Duration timeout = props.defaultTimeout();
    JsonNode timeoutNode = node.path("timeoutMs");
    if (timeoutNode.isNumber() && timeoutNode.asLong() > 0) {
      timeout = Duration.ofMillis(timeoutNode.asLong());
    }
    if (timeout.compareTo(props.maxTimeout()) > 0) {
      timeout = props.maxTimeout();
    }

    Set<String> pathVariables = new LinkedHashSet<>();
    Matcher m = PLACEHOLDER.matcher(endpoint);
    while (m.find()) {
      pathVariables.add(m.group(1));
    }
    return new RestToolConfig(
        endpoint,
        HttpMethod.valueOf(normalized),
        Map.copyOf(headers),
        timeout,
        Set.copyOf(pathVariables),
        connection);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    String text = value.asString().trim();
    return text.isEmpty() ? null : text;
  }
}
