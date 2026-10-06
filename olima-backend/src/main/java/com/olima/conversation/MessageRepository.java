package com.olima.conversation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

  List<MessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID convId, Pageable pageable);
}
