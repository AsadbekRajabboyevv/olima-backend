package com.olima.organization;

import com.olima.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity extends BaseEntity {

  private String name;
  private String slug;
  private String description;
  private boolean enabled;

  @Column(name = "widget_key", unique = true)
  private String widgetKey;

  @Column(name = "widget_greeting", length = 300)
  private String widgetGreeting;

  @Column(name = "widget_greeting_enabled", nullable = false)
  @Builder.Default
  private boolean widgetGreetingEnabled = true;

  @Column(name = "web_search_enabled", nullable = false)
  @Builder.Default
  private boolean webSearchEnabled = false;

  @Column(name = "monthly_token_quota")
  private Long monthlyTokenQuota;
}
