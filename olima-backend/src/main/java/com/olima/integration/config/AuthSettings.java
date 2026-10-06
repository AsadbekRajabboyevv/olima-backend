package com.olima.integration.config;

import com.olima.integration.enums.AuthType;
import java.util.Map;

public record AuthSettings(
    String tokenPath,
    String bodyFormat,
    String usernameField,
    String passwordField,
    Map<String, String> extraFields,
    String tokenJsonPath,
    String expiresInJsonPath,
    Long tokenTtlSeconds,
    String headerName,
    String headerPrefix,
    String scope) {

  public static final String JSON = "JSON";
  public static final String FORM = "FORM";

  public static AuthSettings empty() {
    return new AuthSettings(null, null, null, null, null, null, null, null, null, null, null);
  }

  public AuthSettings withDefaults(AuthType type, long defaultTtlSeconds) {
    boolean oauth = type == AuthType.OAUTH2_CLIENT_CREDENTIALS;
    return new AuthSettings(
        tokenPath,
        or(bodyFormat, oauth ? FORM : JSON).toUpperCase(),
        or(usernameField, "username"),
        or(passwordField, "password"),
        extraFields != null ? extraFields : Map.of(),
        or(tokenJsonPath, "access_token"),
        or(expiresInJsonPath, "expires_in"),
        tokenTtlSeconds != null && tokenTtlSeconds > 0 ? tokenTtlSeconds : defaultTtlSeconds,
        or(headerName, type == AuthType.API_KEY ? "X-API-Key" : "Authorization"),
        headerPrefix != null ? headerPrefix : (type == AuthType.API_KEY ? "" : "Bearer "),
        scope);
  }

  private static String or(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
