package com.olima.security.validator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.olima.security.config.SecurityProperties;
import com.olima.security.model.UserSession;
import com.olima.user.UserRepository;
import com.olima.user.dto.UserSessionView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserSessionValidator {

  private final UserRepository userRepository;
  private final Cache<UUID, Optional<UserSession>> cache;

  public UserSessionValidator(UserRepository userRepository, SecurityProperties properties) {
    this.userRepository = userRepository;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.sessionCacheTtl())
            .maximumSize(10_000)
            .build();
  }

  public boolean isActive(UUID userId, int tokenVersion) {
    Optional<UserSession> session =
        cache.get(
            userId,
            id ->
                userRepository
                    .findSessionView(id)
                    .map(
                        (UserSessionView v) ->
                            new UserSession(v.isEnabled(), v.getTokenVersion())));
    return session.map(s -> s.enabled() && s.tokenVersion() == tokenVersion).orElse(false);
  }

  public void invalidate(UUID userId) {
    cache.invalidate(userId);
  }
}
