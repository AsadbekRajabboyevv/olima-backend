package com.olima.common.http;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.util.unit.DataSize;

public record Request(
    HttpMethod method,
    URI uri,
    Map<String, String> headers,
    byte[] body,
    String contentType,
    Duration timeout,
    DataSize maxSize) {

  public static Request of(HttpMethod method, URI uri) {
    return new Request(method, uri, Map.of(), null, null, null, null);
  }

  public Request withHeaders(Map<String, String> extra) {
    Map<String, String> merged = new LinkedHashMap<>(headers);
    if (extra != null) {
      merged.putAll(extra);
    }
    return new Request(method, uri, Map.copyOf(merged), body, contentType, timeout, maxSize);
  }

  public Request withBody(byte[] newBody, String newContentType) {
    return new Request(method, uri, headers, newBody, newContentType, timeout, maxSize);
  }

  public Request withTimeout(Duration newTimeout) {
    return new Request(method, uri, headers, body, contentType, newTimeout, maxSize);
  }

  public Request withMaxSize(DataSize newMaxSize) {
    return new Request(method, uri, headers, body, contentType, timeout, newMaxSize);
  }
}
