package com.olima.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationConnectionRepository
    extends JpaRepository<IntegrationConnectionEntity, UUID> {

  List<IntegrationConnectionEntity> findByOrganizationIdOrderByNameAsc(UUID organizationId);

  Optional<IntegrationConnectionEntity> findByOrganizationIdAndName(
      UUID organizationId, String name);

  boolean existsByOrganizationIdAndName(UUID organizationId, String name);

  @Query("select c.organizationId from IntegrationConnectionEntity c where c.id = :id")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);
}
