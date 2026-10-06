package com.olima.complaint;

import com.olima.common.BaseEntity;
import com.olima.complaint.enums.ComplaintStatus;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintEntity extends BaseEntity {

  private UUID organizationId;
  private UUID conversationId;
  private String subject;
  private String description;
  private String category;

  @Enumerated(EnumType.STRING)
  private ComplaintStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String contactInfo;
}
