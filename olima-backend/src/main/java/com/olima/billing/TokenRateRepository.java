package com.olima.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRateRepository extends JpaRepository<TokenRateEntity, UUID> {

  Optional<TokenRateEntity> findByOrganizationIdIsNull();

  Optional<TokenRateEntity> findByOrganizationId(UUID organizationId);

  List<TokenRateEntity> findByOrganizationIdIsNotNull();
}
