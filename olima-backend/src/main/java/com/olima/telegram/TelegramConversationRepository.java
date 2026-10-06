package com.olima.telegram;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramConversationRepository
    extends JpaRepository<TelegramConversationEntity, UUID> {

  Optional<TelegramConversationEntity> findByOrganizationIdAndTelegramChatId(
      UUID organizationId, Long telegramChatId);
}
