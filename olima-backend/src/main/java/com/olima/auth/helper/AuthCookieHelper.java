package com.olima.auth.helper;

import com.olima.auth.dto.LoginResponse;
import com.olima.security.config.SecurityProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieHelper {

  private final SecurityProperties securityProperties;

  public ResponseEntity<LoginResponse> toResponseWithCookie(LoginResponse response) {
    Duration maxAge = Duration.between(Instant.now(), response.refreshExpiresAt());
    ResponseCookie cookie = createCookie(response.refreshToken(), maxAge);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(response.withoutRefresh());
  }

  public ResponseCookie createLogoutCookie() {
    return createCookie("", Duration.ZERO);
  }

  public ResponseCookie createCookie(String value, Duration maxAge) {
    SecurityProperties.Refresh cfg = securityProperties.refresh();
    return ResponseCookie.from(cfg.cookieName(), value)
        .httpOnly(true)
        .secure(cfg.cookieSecure())
        .sameSite(cfg.cookieSameSite())
        .path(cfg.cookiePath())
        .maxAge(maxAge)
        .build();
  }
}
