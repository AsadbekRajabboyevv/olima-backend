package com.olima.auth;

import com.olima.auth.dto.LoginRequest;
import com.olima.auth.dto.LoginResponse;
import com.olima.security.model.AuthenticatedUser;

public interface AuthService {

  LoginResponse login(LoginRequest request, String ip, String userAgent);

  LoginResponse refresh(String refreshToken, String ip, String userAgent);

  void logout(String refreshToken);

  void logoutEverywhere(AuthenticatedUser principal);

  LoginResponse describe(AuthenticatedUser principal);
}
