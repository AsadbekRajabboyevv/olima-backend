package com.olima.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.olima.auth.dto.Attempts;
import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.security.config.SecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

  private final SecurityProperties.Login config;
  private final Cache<String, Attempts> attempts;

  public LoginAttemptServiceImpl(SecurityProperties properties) {
    this.config = properties.login();
    this.attempts =
        Caffeine.newBuilder()
            .expireAfterWrite(config.lockDuration().multipliedBy(2))
            .maximumSize(100_000)
            .build();
  }

  @Override
  public void checkNotLocked(String username) {
    Attempts a = attempts.getIfPresent(key(username));
    if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now())) {
      Duration left = Duration.between(Instant.now(), a.lockedUntil());
      BusinessException e =
          new BusinessException(
              ErrorCode.LOGIN_LOCKED,
              "Juda ko'p muvaffaqiyatsiz urinish. "
                  + Math.max(1, left.toMinutes())
                  + " daqiqadan keyin qayta urinib ko'ring",
              null,
              left);
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
  }

  @Override
  public void recordFailure(String username) {
    attempts
        .asMap()
        .compute(
            key(username),
            (k, a) -> {
              int failures = (a == null ? 0 : a.failures()) + 1;
              Instant lockedUntil =
                  failures >= config.maxFailures()
                      ? Instant.now().plus(config.lockDuration())
                      : null;
              return new Attempts(lockedUntil != null ? 0 : failures, lockedUntil);
            });
  }

  @Override
  public void recordSuccess(String username) {
    attempts.invalidate(key(username));
  }

  private static String key(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }
}
