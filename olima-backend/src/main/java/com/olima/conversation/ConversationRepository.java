package com.olima.conversation;

import com.olima.conversation.dto.ConversationAccessView;
import com.olima.conversation.dto.ConversationSummary;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

  @EntityGraph(attributePaths = {"messages"})
  @Override
  @NonNull Optional<ConversationEntity> findById(@NonNull UUID id);

  @Query(
      """
      select
             c.organizationId as organizationId,
             c.accessTokenHash as accessTokenHash
      from ConversationEntity c where c.id = :id
      """)
  Optional<ConversationAccessView> findAccessView(@Param("id") UUID id);

  @Query("select c.organizationId from ConversationEntity c where c.id = :id")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);

  @Query(
      value =
          """
          select new com.olima.conversation.dto.ConversationSummary(
                 c.id, c.organizationId, c.title, count(m.id), c.createdAt, max(m.createdAt))
          from ConversationEntity c left join c.messages m
          where c.organizationId = :orgId
          group by c.id, c.organizationId, c.title, c.createdAt
          """,
      countQuery = "select count(c) from ConversationEntity c where c.organizationId = :orgId")
  Page<ConversationSummary> findSummaries(@Param("orgId") UUID orgId, Pageable pageable);
}
