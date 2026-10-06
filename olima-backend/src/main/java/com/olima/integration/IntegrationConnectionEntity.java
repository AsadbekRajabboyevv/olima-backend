package com.olima.integration;

import com.olima.common.BaseEntity;
import com.olima.integration.enums.AuthType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "integration_connections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConnectionEntity extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(name = "base_url", nullable = false)
  private String baseUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_type", nullable = false)
  private AuthType authType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "auth_config", columnDefinition = "jsonb")
  private String authConfig;

  @Convert(converter = EncryptedStringConverter.class)
  private String username;

  @Convert(converter = EncryptedStringConverter.class)
  private String password;

  @Convert(converter = EncryptedStringConverter.class)
  private String secret;

  @Builder.Default private boolean enabled = true;
}
