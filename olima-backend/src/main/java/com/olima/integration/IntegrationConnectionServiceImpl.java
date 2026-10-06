package com.olima.integration;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.common.error.NotFoundException;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.integration.auth.ConnectionAuthenticator;
import com.olima.integration.config.AuthSettings;
import com.olima.integration.config.IntegrationProperties;
import com.olima.integration.dto.ConnectionRequest;
import com.olima.integration.dto.ConnectionResponse;
import com.olima.integration.dto.ConnectionSnapshot;
import com.olima.integration.dto.ConnectionTestResponse;
import com.olima.integration.enums.AuthType;
import com.olima.tool.ToolConnectionUsage;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class IntegrationConnectionServiceImpl implements IntegrationConnectionService {

  private final IntegrationConnectionRepository repository;
  private final ToolConnectionUsage toolUsage;
  private final ConnectionAuthenticator authenticator;
  private final OutboundUrlPolicy urlPolicy;
  private final ObjectMapper objectMapper;
  private final long defaultTtlSeconds;

  public IntegrationConnectionServiceImpl(
      IntegrationConnectionRepository repository,
      ToolConnectionUsage toolUsage,
      ConnectionAuthenticator authenticator,
      OutboundUrlPolicy urlPolicy,
      ObjectMapper objectMapper,
      IntegrationProperties properties) {
    this.repository = repository;
    this.toolUsage = toolUsage;
    this.authenticator = authenticator;
    this.urlPolicy = urlPolicy;
    this.objectMapper = objectMapper;
    this.defaultTtlSeconds = properties.defaultTokenTtl().toSeconds();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConnectionResponse> findByOrganization(UUID organizationId) {
    List<ConnectionResponse> list =
        repository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
            .map(this::toResponse)
            .toList();
    return list;
  }

  @Override
  @Transactional(readOnly = true)
  public ConnectionResponse findById(UUID id) {
    ConnectionResponse response = toResponse(require(id));
    return response;
  }

  @Override
  @Transactional
  public ConnectionResponse create(UUID organizationId, ConnectionRequest request) {
    if (repository.existsByOrganizationIdAndName(organizationId, request.name())) {
      BusinessException e =
          new BusinessException(
              ErrorCode.DUPLICATE_NAME,
              "Bu tashkilotda '" + request.name() + "' nomli ulanish allaqachon bor");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    IntegrationConnectionEntity entity =
        IntegrationConnectionEntity.builder().organizationId(organizationId).build();
    apply(entity, request);
    ConnectionResponse response = toResponse(repository.save(entity));
    return response;
  }

  @Override
  @Transactional
  public ConnectionResponse update(UUID id, ConnectionRequest request) {
    IntegrationConnectionEntity entity = require(id);
    if (!entity.getName().equals(request.name())) {
      if (repository.existsByOrganizationIdAndName(entity.getOrganizationId(), request.name())) {
        BusinessException e =
            new BusinessException(
                ErrorCode.DUPLICATE_NAME,
                "Bu tashkilotda '" + request.name() + "' nomli ulanish allaqachon bor");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
      if (toolUsage.countToolsUsing(entity.getOrganizationId(), entity.getName()) > 0) {
        BusinessException e =
            new BusinessException(
                ErrorCode.INVALID_STATE,
                "Ulanish vositalarda ishlatilmoqda — avval vositalardagi nomni o'zgartiring");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
    }
    apply(entity, request);
    IntegrationConnectionEntity saved = repository.save(entity);

    authenticator.invalidate(id);
    ConnectionResponse response = toResponse(saved);
    return response;
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    IntegrationConnectionEntity entity = require(id);
    long tools = toolUsage.countToolsUsing(entity.getOrganizationId(), entity.getName());
    if (tools > 0) {
      BusinessException e =
          new BusinessException(
              ErrorCode.INVALID_STATE,
              "Ulanish "
                  + tools
                  + " ta vositada ishlatilmoqda — avval ularni o'chiring yoki boshqa "
                  + "ulanishga o'tkazing");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    repository.delete(entity);
    authenticator.invalidate(id);
  }

  @Override
  @Transactional(readOnly = true)
  public ConnectionTestResponse test(UUID id) {
    ConnectionSnapshot snapshot = toSnapshot(require(id));
    try {
      authenticator.verify(snapshot);
      ConnectionTestResponse response =
          new ConnectionTestResponse(
              true,
              snapshot.authType().issuesTokens()
                  ? "Token muvaffaqiyatli olindi"
                  : "Sozlama to'g'ri (token olish talab qilinmaydi)");
      return response;
    } catch (BusinessException e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      return new ConnectionTestResponse(false, e.getMessage());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public ConnectionSnapshot snapshot(UUID organizationId, String name) {
    IntegrationConnectionEntity entity =
        repository
            .findByOrganizationIdAndName(organizationId, name)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("snapshot.connection_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    if (!entity.isEnabled()) {
      BusinessException e =
          new BusinessException(ErrorCode.INVALID_STATE, "Connection '" + name + "' is disabled");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    ConnectionSnapshot snapshot = toSnapshot(entity);
    return snapshot;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID organizationId, String name) {
    return repository.existsByOrganizationIdAndName(organizationId, name);
  }

  private void apply(IntegrationConnectionEntity entity, ConnectionRequest request) {
    URI base = urlPolicy.requireAllowed(request.baseUrl().trim());
    if (base.getRawQuery() != null || request.baseUrl().contains("{")) {
      IllegalArgumentException e =
          new IllegalArgumentException("baseUrl must not contain query or placeholders");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    AuthType type = request.authType();
    AuthSettings settings =
        request.authConfig() != null ? request.authConfig() : AuthSettings.empty();

    entity.setName(request.name());
    entity.setDescription(blankToNull(request.description()));
    entity.setBaseUrl(base.toString().replaceAll("/+$", ""));
    entity.setAuthType(type);
    entity.setAuthConfig(objectMapper.writeValueAsString(settings));
    if (request.enabled() != null) {
      entity.setEnabled(request.enabled());
    }

    if (notBlank(request.username())) {
      entity.setUsername(request.username().trim());
    }
    if (notBlank(request.password())) {
      entity.setPassword(request.password());
    }
    if (notBlank(request.secret())) {
      entity.setSecret(request.secret().trim());
    }

    if (!type.needsUsernameAndPassword()) {
      entity.setUsername(null);
      entity.setPassword(null);
    }
    if (!type.needsSecret()) {
      entity.setSecret(null);
    }

    if (type.needsUsernameAndPassword()
        && (!notBlank(entity.getUsername()) || !notBlank(entity.getPassword()))) {
      IllegalArgumentException e =
          new IllegalArgumentException(type + " uchun username va password majburiy");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (type.needsSecret() && !notBlank(entity.getSecret())) {
      IllegalArgumentException e =
          new IllegalArgumentException(type + " uchun secret (token/kalit) majburiy");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (type.issuesTokens() && !notBlank(settings.tokenPath())) {
      IllegalArgumentException e =
          new IllegalArgumentException(
              type + " uchun authConfig.tokenPath majburiy " + "(masalan: /api/auth/login)");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (type.issuesTokens()) {
      authenticator.resolve(toSnapshot(entity), settings.tokenPath());
    }
  }

  private ConnectionSnapshot toSnapshot(IntegrationConnectionEntity e) {
    return new ConnectionSnapshot(
        e.getId(),
        e.getOrganizationId(),
        e.getName(),
        URI.create(e.getBaseUrl()),
        e.getAuthType(),
        settings(e).withDefaults(e.getAuthType(), defaultTtlSeconds),
        e.getUsername(),
        e.getPassword(),
        e.getSecret());
  }

  private AuthSettings settings(IntegrationConnectionEntity e) {
    if (e.getAuthConfig() == null || e.getAuthConfig().isBlank()) {
      return AuthSettings.empty();
    }
    return objectMapper.readValue(e.getAuthConfig(), AuthSettings.class);
  }

  private ConnectionResponse toResponse(IntegrationConnectionEntity e) {
    return new ConnectionResponse(
        e.getId(),
        e.getOrganizationId(),
        e.getName(),
        e.getDescription(),
        e.getBaseUrl(),
        e.getAuthType(),
        settings(e),
        notBlank(e.getUsername()),
        notBlank(e.getPassword()),
        notBlank(e.getSecret()),
        e.isEnabled(),
        toolUsage.countToolsUsing(e.getOrganizationId(), e.getName()),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  private IntegrationConnectionEntity require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> {
              NotFoundException e = new NotFoundException("get.connection_not_found");
              log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
              return e;
            });
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String blankToNull(String value) {
    return notBlank(value) ? value.trim() : null;
  }
}
