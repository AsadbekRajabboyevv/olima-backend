package com.olima.auth;

import com.olima.auth.dto.LoginRequest;
import com.olima.auth.dto.LoginResponse;
import com.olima.auth.exception.InvalidCredentialsException;
import com.olima.auth.token.Issued;
import com.olima.auth.token.RefreshTokenService;
import com.olima.auth.token.Rotation;
import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.organization.OrganizationEntity;
import com.olima.organization.OrganizationRepository;
import com.olima.security.JwtService;
import com.olima.security.model.AuthenticatedUser;
import com.olima.security.model.IssuedToken;
import com.olima.security.validator.UserSessionValidator;
import com.olima.user.UserEntity;
import com.olima.user.UserRepository;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final String INVALID = "Invalid username or password";

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokens;
  private final LoginAttemptService loginAttempts;
  private final UserSessionValidator sessionValidator;

  private String dummyHash;

  @PostConstruct
  void initDummyHash() {
    dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Override
  @Transactional
  public LoginResponse login(LoginRequest request, String ip, String userAgent) {
    loginAttempts.checkNotLocked(request.username());
    UserEntity user = userRepository.findByUsername(request.username()).orElse(null);
    String hash = user != null ? user.getPasswordHash() : dummyHash;
    boolean passwordOk = passwordEncoder.matches(request.password(), hash);

    if (user == null || !passwordOk || !user.isEnabled() || !isOrganizationActive(user)) {
      loginAttempts.recordFailure(request.username());
      InvalidCredentialsException e = new InvalidCredentialsException(INVALID);
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    loginAttempts.recordSuccess(request.username());
    LoginResponse response = issue(user, refreshTokens.issue(user.getId(), ip, userAgent));
    return response;
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public LoginResponse refresh(String refreshToken, String ip, String userAgent) {
    Rotation rotation = refreshTokens.rotate(refreshToken, ip, userAgent);
    UserEntity user = userRepository.findById(rotation.userId()).orElse(null);
    if (user == null || !user.isEnabled() || !isOrganizationActive(user)) {
      refreshTokens.revokeAll(rotation.userId());
      BusinessException e =
          new BusinessException(
              ErrorCode.INVALID_REFRESH_TOKEN, "Session expired, please log in again");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    LoginResponse response = issue(user, rotation.next());
    return response;
  }

  @Override
  public void logout(String refreshToken) {
    refreshTokens.revoke(refreshToken);
  }

  @Override
  @Transactional
  public void logoutEverywhere(AuthenticatedUser principal) {
    userRepository
        .findById(principal.userId())
        .ifPresent(
            user -> {
              user.setTokenVersion(user.getTokenVersion() + 1);
              userRepository.save(user);
            });
    refreshTokens.revokeAll(principal.userId());
    sessionValidator.invalidate(principal.userId());
  }

  @Override
  @Transactional(readOnly = true)
  public LoginResponse describe(AuthenticatedUser principal) {
    LoginResponse response =
        new LoginResponse(
            null,
            null,
            principal.username(),
            principal.role(),
            principal.organizationId(),
            organizationName(principal.organizationId()),
            null,
            null);
    return response;
  }

  private LoginResponse issue(UserEntity user, Issued refresh) {
    IssuedToken access = jwtService.generateToken(user);
    return new LoginResponse(
        access.token(),
        access.expiresAt(),
        user.getUsername(),
        user.getRole(),
        user.getOrganizationId(),
        organizationName(user.getOrganizationId()),
        refresh.token(),
        refresh.expiresAt());
  }

  private boolean isOrganizationActive(UserEntity user) {
    return user.getOrganizationId() == null
        || organizationRepository
            .findById(user.getOrganizationId())
            .map(OrganizationEntity::isEnabled)
            .orElse(false);
  }

  private String organizationName(UUID organizationId) {
    if (organizationId == null) {
      return null;
    }
    return organizationRepository
        .findById(organizationId)
        .map(OrganizationEntity::getName)
        .orElse(null);
  }
}
