package com.olima.telegram;

import com.olima.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "telegram_conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramConversationEntity extends BaseEntity {

  private UUID organizationId;

  @Column(name = "telegram_chat_id")
  private Long telegramChatId;

  @Column(name = "conversation_id")
  private UUID conversationId;
}
