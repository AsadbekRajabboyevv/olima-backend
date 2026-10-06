package com.olima.tool;

import com.olima.common.BaseEntity;
import com.olima.tool.enums.AccessLevel;
import com.olima.tool.enums.ToolType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tools")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolEntity extends BaseEntity {

  private UUID organizationId;
  private String name;
  private String description;

  @Enumerated(EnumType.STRING)
  private ToolType type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String configuration;

  private boolean enabled;
  private boolean requiresConfirmation;

  @Enumerated(EnumType.STRING)
  private AccessLevel accessLevel;

  @OneToMany(
      mappedBy = "tool",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @Builder.Default
  private List<ToolParameterEntity> parameters = new ArrayList<>();
}
