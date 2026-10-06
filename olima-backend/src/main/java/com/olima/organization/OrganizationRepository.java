package com.olima.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
  Optional<OrganizationEntity> findBySlug(String slug);

  Optional<OrganizationEntity> findByWidgetKeyAndEnabledTrue(String widgetKey);
}
