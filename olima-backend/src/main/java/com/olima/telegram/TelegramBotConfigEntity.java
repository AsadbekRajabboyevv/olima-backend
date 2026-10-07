package com.olima.telegram;

import com.olima.common.BaseEntity;
import com.olima.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "telegram_bot_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramBotConfigEntity extends BaseEntity {

  private UUID organizationId;

  @Column(name = "bot_token")
  @Convert(converter = EncryptedStringConverter.class)
  private String botToken;

  @Column(name = "bot_username")
  private String botUsername;

  @Column(name = "webhook_secret")
  private String webhookSecret;

  @Builder.Default private boolean enabled = true;

  @Column(name = "notification_chat_id")
  private Long notificationChatId;

  @Enumerated(EnumType.STRING)
  @Column(name = "update_mode", nullable = false)
  @Builder.Default
  private TelegramUpdateMode updateMode = TelegramUpdateMode.WEBHOOK;
}
