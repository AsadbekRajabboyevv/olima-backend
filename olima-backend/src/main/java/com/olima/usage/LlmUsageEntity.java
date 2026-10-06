package com.olima.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "llm_usage")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  private String channel;

  private String model;

  @Column(name = "prompt_tokens", nullable = false)
  private int promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private int completionTokens;

  @Column(name = "cost_uzs", nullable = false)
  @Builder.Default
  private BigDecimal costUzs = BigDecimal.ZERO;

  @Column(name = "created_at", updatable = false)
  @CreationTimestamp
  private Instant createdAt;
}
