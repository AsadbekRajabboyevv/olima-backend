package com.olima.auth;

import com.olima.auth.dto.LoginRequest;
import com.olima.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/auth")
public interface AuthApi {

  @PostMapping("/login")
  ResponseEntity<LoginResponse> login(
      @RequestBody @Valid LoginRequest request, HttpServletRequest http);

  @PostMapping("/refresh")
  ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = "${app.security.refresh.cookie-name:olima_refresh}", required = false)
          String refreshToken,
      HttpServletRequest http);

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @CookieValue(name = "${app.security.refresh.cookie-name:olima_refresh}", required = false)
          String refreshToken);

  @PostMapping("/logout-all")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<Void> logoutEverywhere();

  @GetMapping("/me")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<LoginResponse> me();
}
