package com.olima.user;

import com.olima.user.dto.UserSessionView;
import com.olima.user.enums.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  List<UserEntity> findByOrganizationId(UUID organizationId);

  boolean existsByRole(UserRole role);

  @Query(
      "select u.enabled as enabled, u.tokenVersion as tokenVersion from UserEntity u "
          + "where u.id = :id")
  Optional<UserSessionView> findSessionView(@Param("id") UUID id);
}
