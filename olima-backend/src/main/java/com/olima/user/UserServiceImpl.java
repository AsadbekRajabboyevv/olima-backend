package com.olima.user;

import com.olima.auth.policy.PasswordPolicy;
import com.olima.auth.token.RefreshTokenService;
import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.common.error.NotFoundException;
import com.olima.organization.OrganizationService;
import com.olima.security.tenant.CurrentUser;
import com.olima.security.validator.UserSessionValidator;
import com.olima.user.dto.CreateUserRequest;
import com.olima.user.dto.ResetPasswordRequest;
import com.olima.user.dto.UserResponse;
import com.olima.user.enums.UserRole;
import com.olima.user.exception.DuplicateUsernameException;
import java.util.List;
import java.util.Map;
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
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final OrganizationService organizationService;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final RefreshTokenService refreshTokens;
  private final UserSessionValidator sessionValidator;

  @Override
  @Transactional(readOnly = true)
  public List<UserResponse> findAll() {
    List<UserEntity> users = userRepository.findAll();
    Map<UUID, String> orgNames = organizationService.findAllNames();
    List<UserResponse> list =
        users.stream()
            .map(
                u ->
                    toResponse(
                        u,
                        u.getOrganizationId() != null ? orgNames.get(u.getOrganizationId()) : null))
            .toList();
    return list;
  }

  @Override
  @Transactional
  public UserResponse create(CreateUserRequest request) {
    if (request.role() == UserRole.WIDGET) {
      IllegalArgumentException e =
          new IllegalArgumentException("WIDGET role cannot be assigned to a user");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (userRepository.existsByUsername(request.username())) {
      DuplicateUsernameException e =
          new DuplicateUsernameException("Username already taken: " + request.username());
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    passwordPolicy.validate(request.password());

    String organizationName = null;
    if (request.role() == UserRole.ORG_ADMIN) {
      if (request.organizationId() == null) {
        IllegalArgumentException e =
            new IllegalArgumentException("organizationId is required for ORG_ADMIN users");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
      organizationName = organizationService.findById(request.organizationId()).name();
    }

    UserEntity user =
        UserEntity.builder()
            .username(request.username().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(request.role())
            .organizationId(request.role() == UserRole.ORG_ADMIN ? request.organizationId() : null)
            .enabled(true)
            .build();
    UserResponse response = toResponse(userRepository.save(user), organizationName);
    return response;
  }

  @Override
  @Transactional
  public UserResponse resetPassword(UUID id, ResetPasswordRequest request) {
    UserEntity user = require(id);
    passwordPolicy.validate(request.password());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    revokeSessions(user);
    UserResponse response = toResponse(userRepository.save(user), organizationName(user));
    return response;
  }

  @Override
  @Transactional
  public UserResponse setEnabled(UUID id, boolean enabled) {
    UserEntity user = require(id);
    if (!enabled) {
      guardSelfAndLastSuperAdmin(user);
      revokeSessions(user);
    }
    user.setEnabled(enabled);
    UserResponse response = toResponse(userRepository.save(user), organizationName(user));
    return response;
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    UserEntity user = require(id);
    guardSelfAndLastSuperAdmin(user);
    userRepository.delete(user);
    sessionValidator.invalidate(id);
  }

  private void guardSelfAndLastSuperAdmin(UserEntity user) {
    if (CurrentUser.get().map(u -> u.userId().equals(user.getId())).orElse(false)) {
      BusinessException e =
          new BusinessException(
              ErrorCode.INVALID_STATE, "O'zingizni o'chira yoki bloklay olmaysiz");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (user.getRole() == UserRole.SUPER_ADMIN
        && userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.SUPER_ADMIN && u.isEnabled())
                .count()
            <= 1) {
      BusinessException e =
          new BusinessException(
              ErrorCode.INVALID_STATE, "Oxirgi faol super adminni o'chirib bo'lmaydi");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
  }

  private void revokeSessions(UserEntity user) {
    user.setTokenVersion(user.getTokenVersion() + 1);
    refreshTokens.revokeAll(user.getId());
    sessionValidator.invalidate(user.getId());
  }

  private UserEntity require(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () -> {
              NotFoundException e = new NotFoundException("get.user_not_found");
              log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
              return e;
            });
  }

  private String organizationName(UserEntity user) {
    return user.getOrganizationId() == null
        ? null
        : organizationService
            .findNames(List.of(user.getOrganizationId()))
            .get(user.getOrganizationId());
  }

  private UserResponse toResponse(UserEntity user, String organizationName) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        user.getOrganizationId(),
        organizationName,
        user.isEnabled(),
        user.getCreatedAt());
  }
}
