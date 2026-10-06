package com.olima.auth.token;

import java.util.UUID;

public interface RefreshTokenService {

  Issued issue(UUID userId, String ip, String userAgent);

  Rotation rotate(String rawToken, String ip, String userAgent);

  void revoke(String rawToken);

  void revokeAll(UUID userId);

  void deleteExpired();
}
