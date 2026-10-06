package com.olima.organization;

import com.olima.organization.dto.AssistantSettingsRequest;
import com.olima.organization.dto.AssistantSettingsResponse;
import com.olima.organization.dto.OrganizationRequest;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.organization.dto.WidgetConfigResponse;
import com.olima.organization.dto.WidgetSettingsRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OrganizationService {

  List<OrganizationResponse> findAll();

  List<OrganizationResponse> findAccessibleOrganizations();

  OrganizationResponse findById(UUID id);

  boolean exists(UUID id);

  /** Berilgan tashkilotlarning nomlari (id → name); topilmaganlari xaritada bo'lmaydi. */
  Map<UUID, String> findNames(Collection<UUID> ids);

  /** Barcha tashkilotlarning nomlari (id → name). */
  Map<UUID, String> findAllNames();

  OrganizationResponse findBySlug(String slug);

  OrganizationResponse create(OrganizationRequest request);

  OrganizationResponse update(UUID id, OrganizationRequest request);

  void delete(UUID id);

  OrganizationResponse updateWidgetSettings(UUID id, WidgetSettingsRequest request);

  WidgetConfigResponse widgetConfig(UUID id);

  AssistantSettingsResponse assistantSettings(UUID id);

  AssistantSettingsResponse updateAssistantSettings(UUID id, AssistantSettingsRequest request);

  OrganizationResponse rotateWidgetKey(UUID id);
}
