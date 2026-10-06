package com.olima.billing;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "token_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "input_price_per_million", nullable = false)
  private BigDecimal inputPricePerMillion;

  @Column(name = "output_price_per_million", nullable = false)
  private BigDecimal outputPricePerMillion;

  @Column(name = "updated_by")
  private String updatedBy;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;
}
