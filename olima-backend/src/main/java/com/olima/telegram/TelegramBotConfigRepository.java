package com.olima.telegram;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramBotConfigRepository extends JpaRepository<TelegramBotConfigEntity, UUID> {

  Optional<TelegramBotConfigEntity> findByOrganizationId(UUID organizationId);

  Optional<TelegramBotConfigEntity> findByWebhookSecret(String webhookSecret);
}
