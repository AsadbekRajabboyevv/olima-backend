package com.olima.execution;

import com.olima.execution.enums.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tool_executions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private UUID organizationId;
  private UUID conversationId;
  private UUID toolId;
  private String toolName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String input;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String output;

  @Enumerated(EnumType.STRING)
  private ExecutionStatus status;

  private Long durationMs;
  private String errorMessage;

  @Column(name = "created_at", updatable = false)
  @CreationTimestamp
  private Instant createdAt;
}
