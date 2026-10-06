package com.olima.common.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Response(
    int status, String contentType, byte[] body, boolean truncated, URI finalUri) {

  private static final Pattern CHARSET = Pattern.compile("(?i)charset=\"?([\\w.:-]+)\"?");

  public boolean isSuccess() {
    return status >= 200 && status < 300;
  }

  public String bodyAsString() {
    return new String(body, charset());
  }

  public Charset charset() {
    if (contentType != null) {
      Matcher m = CHARSET.matcher(contentType);
      if (m.find()) {
        try {
          return Charset.forName(m.group(1));
        } catch (Exception ignored) {
        }
      }
    }
    return StandardCharsets.UTF_8;
  }
}
