package com.olima.complaint;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintRepository extends JpaRepository<ComplaintEntity, UUID> {
  Page<ComplaintEntity> findByOrganizationId(UUID orgId, Pageable pageable);

  @Query("select c.organizationId from ComplaintEntity c where c.id = :id")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);
}
