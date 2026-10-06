package com.olima.auth;

import com.olima.auth.dto.LoginRequest;
import com.olima.auth.dto.LoginResponse;
import com.olima.auth.helper.AuthCookieHelper;
import com.olima.security.tenant.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthService authService;
  private final AuthCookieHelper cookieHelper;

  @Override
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @RequestBody @Valid LoginRequest request, HttpServletRequest http) {
    LoginResponse response =
        authService.login(request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    return cookieHelper.toResponseWithCookie(response);
  }

  @Override
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = "${app.security.refresh.cookie-name:olima_refresh}", required = false)
          String refreshToken,
      HttpServletRequest http) {
    LoginResponse response =
        authService.refresh(
            refreshToken, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    return cookieHelper.toResponseWithCookie(response);
  }

  @Override
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "${app.security.refresh.cookie-name:olima_refresh}", required = false)
          String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookieHelper.createLogoutCookie().toString())
        .build();
  }

  @Override
  @PostMapping("/logout-all")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<Void> logoutEverywhere() {
    authService.logoutEverywhere(CurrentUser.require());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookieHelper.createLogoutCookie().toString())
        .build();
  }

  @Override
  @GetMapping("/me")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<LoginResponse> me() {
    return ResponseEntity.ok(authService.describe(CurrentUser.require()));
  }
}
