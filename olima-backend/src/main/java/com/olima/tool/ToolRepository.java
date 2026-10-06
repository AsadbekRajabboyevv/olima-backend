package com.olima.tool;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolRepository extends JpaRepository<ToolEntity, UUID> {

  List<ToolEntity> findByOrganizationIdAndEnabledTrue(UUID orgId);

  List<ToolEntity> findByOrganizationId(UUID orgId);

  Optional<ToolEntity> findByOrganizationIdAndName(UUID orgId, String name);

  @Query(
      value =
          "SELECT COUNT(*) FROM tools t WHERE t.organization_id = :orgId "
              + "AND t.configuration ->> 'connection' = :name",
      nativeQuery = true)
  long countByConnection(@Param("orgId") UUID orgId, @Param("name") String name);

  @Query("select t.organizationId from ToolEntity t where t.id = :id")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);
}
