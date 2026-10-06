package com.olima.auth.token;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.security.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

  private static final int TOKEN_BYTES = 32;
  private static final int MAX_USER_AGENT = 300;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final RefreshTokenRepository repository;
  private final SecurityProperties.Refresh config;

  public RefreshTokenServiceImpl(RefreshTokenRepository repository, SecurityProperties properties) {
    this.repository = repository;
    this.config = properties.refresh();
  }

  @Override
  @Transactional
  public Issued issue(UUID userId, String ip, String userAgent) {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = Instant.now().plus(config.ttl());
    repository.save(
        RefreshTokenEntity.builder()
            .userId(userId)
            .tokenHash(hash(raw))
            .expiresAt(expiresAt)
            .createdIp(ip)
            .userAgent(
                userAgent != null && userAgent.length() > MAX_USER_AGENT
                    ? userAgent.substring(0, MAX_USER_AGENT)
                    : userAgent)
            .build());
    return new Issued(raw, expiresAt);
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public Rotation rotate(String rawToken, String ip, String userAgent) {
    RefreshTokenEntity token = find(rawToken);
    Instant now = Instant.now();
    if (token.getRevokedAt() != null) {
      log.warn(
          "Revoked refresh token reused for user {} — revoking all sessions", token.getUserId());
      repository.revokeAllForUser(token.getUserId(), now);
      throw invalid();
    }
    if (token.getExpiresAt().isBefore(now)) {
      throw invalid();
    }
    token.setRevokedAt(now);
    repository.save(token);
    Rotation rotation = new Rotation(token.getUserId(), issue(token.getUserId(), ip, userAgent));
    return rotation;
  }

  @Override
  @Transactional
  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(
            t -> {
              if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                repository.save(t);
              }
            });
  }

  @Override
  @Transactional
  public void revokeAll(UUID userId) {
    repository.revokeAllForUser(userId, Instant.now());
  }

  @Override
  @Scheduled(cron = "${app.security.refresh-cleanup-cron:0 30 3 * * *}")
  @Transactional
  public void deleteExpired() {
    int deleted = repository.deleteExpired(Instant.now());
    if (deleted > 0) {
      log.info("Deleted {} expired refresh token(s)", deleted);
    }
  }

  private RefreshTokenEntity find(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw invalid();
    }
    return repository.findByTokenHash(hash(rawToken)).orElseThrow(this::invalid);
  }

  private BusinessException invalid() {
    BusinessException e =
        new BusinessException(
            ErrorCode.INVALID_REFRESH_TOKEN, "Session expired, please log in again");
    log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
    return e;
  }

  private static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
