package com.olima.organization;

import com.olima.common.error.NotFoundException;
import com.olima.common.util.SlugGenerator;
import com.olima.organization.dto.AssistantSettingsRequest;
import com.olima.organization.dto.AssistantSettingsResponse;
import com.olima.organization.dto.OrganizationRequest;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.organization.dto.WidgetConfigResponse;
import com.olima.organization.dto.WidgetSettingsRequest;
import com.olima.security.model.AuthenticatedUser;
import com.olima.security.tenant.CurrentUser;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final OrganizationMapper organizationMapper;

  @Value("${app.agent.tools.web-search.enabled:true}")
  private boolean webSearchAvailable;

  @Override
  public List<OrganizationResponse> findAll() {
    List<OrganizationResponse> list =
        organizationRepository.findAll().stream().map(organizationMapper::toResponse).toList();
    return list;
  }

  @Override
  public List<OrganizationResponse> findAccessibleOrganizations() {
    AuthenticatedUser principal = CurrentUser.require();
    if (!principal.isSuperAdmin()) {
      List<OrganizationResponse> list = List.of(findById(principal.organizationId()));
      return list;
    }
    List<OrganizationResponse> list = findAll();
    return list;
  }

  @Override
  public OrganizationResponse findById(UUID id) {
    OrganizationResponse response =
        organizationRepository
            .findById(id)
            .map(organizationMapper::toResponse)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    return response;
  }

  @Override
  public boolean exists(UUID id) {
    return id != null && organizationRepository.existsById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> findNames(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    return toNames(organizationRepository.findAllById(ids));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> findAllNames() {
    return toNames(organizationRepository.findAll());
  }

  private static Map<UUID, String> toNames(List<OrganizationEntity> organizations) {
    return organizations.stream()
        .collect(Collectors.toMap(OrganizationEntity::getId, OrganizationEntity::getName));
  }

  @Override
  public OrganizationResponse findBySlug(String slug) {
    OrganizationResponse response =
        organizationRepository
            .findBySlug(slug)
            .map(organizationMapper::toResponse)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    return response;
  }

  @Override
  @Transactional
  public OrganizationResponse create(OrganizationRequest request) {
    OrganizationEntity entity = organizationMapper.toEntity(request);
    entity.setSlug(
        SlugGenerator.unique(
            request.name(), candidate -> organizationRepository.findBySlug(candidate).isPresent()));
    OrganizationEntity saved = organizationRepository.save(entity);
    OrganizationResponse response = organizationMapper.toResponse(saved);
    return response;
  }

  @Override
  @Transactional
  public OrganizationResponse update(UUID id, OrganizationRequest request) {
    OrganizationEntity entity =
        organizationRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("update.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    if (!request.name().equals(entity.getName())) {
      entity.setSlug(
          SlugGenerator.unique(
              request.name(),
              candidate ->
                  organizationRepository
                      .findBySlug(candidate)
                      .filter(o -> !o.getId().equals(id))
                      .isPresent()));
    }
    entity.setName(request.name());
    entity.setDescription(request.description());
    entity.setMonthlyTokenQuota(request.monthlyTokenQuota());
    OrganizationResponse response =
        organizationMapper.toResponse(organizationRepository.save(entity));
    return response;
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    if (!organizationRepository.existsById(id)) {
      NotFoundException e = new NotFoundException("delete.organization_not_found");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    organizationRepository.deleteById(id);
  }

  @Override
  @Transactional
  public OrganizationResponse updateWidgetSettings(UUID id, WidgetSettingsRequest request) {
    OrganizationEntity entity =
        organizationRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("update.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    String greeting = request.greeting() == null ? null : request.greeting().trim();
    entity.setWidgetGreeting(greeting == null || greeting.isEmpty() ? null : greeting);
    entity.setWidgetGreetingEnabled(request.greetingEnabled());
    OrganizationResponse response =
        organizationMapper.toResponse(organizationRepository.save(entity));
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public WidgetConfigResponse widgetConfig(UUID id) {
    OrganizationEntity entity =
        organizationRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    WidgetConfigResponse response =
        new WidgetConfigResponse(entity.getWidgetGreeting(), entity.isWidgetGreetingEnabled());
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public AssistantSettingsResponse assistantSettings(UUID id) {
    OrganizationEntity entity =
        organizationRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    AssistantSettingsResponse response = toAssistantSettings(entity);
    return response;
  }

  @Override
  @Transactional
  public AssistantSettingsResponse updateAssistantSettings(
      UUID id, AssistantSettingsRequest request) {
    OrganizationEntity entity =
        organizationRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("update.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    if (request.webSearchEnabled() && !webSearchAvailable) {
      IllegalStateException e =
          new IllegalStateException("Internetdan qidirish platforma darajasida o'chirilgan");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    entity.setWebSearchEnabled(request.webSearchEnabled());
    AssistantSettingsResponse response = toAssistantSettings(organizationRepository.save(entity));
    return response;
  }

  @Override
  @Transactional
  public OrganizationResponse rotateWidgetKey(UUID id) {
    OrganizationEntity entity =
        organizationRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("rotate.organization_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    entity.setWidgetKey(OrganizationMapper.newWidgetKey());
    OrganizationResponse response =
        organizationMapper.toResponse(organizationRepository.save(entity));
    return response;
  }

  private AssistantSettingsResponse toAssistantSettings(OrganizationEntity entity) {
    return new AssistantSettingsResponse(
        entity.isWebSearchEnabled() && webSearchAvailable, webSearchAvailable);
  }
}
