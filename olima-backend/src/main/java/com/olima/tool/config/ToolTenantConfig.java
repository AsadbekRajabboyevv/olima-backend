package com.olima.tool.config;

import com.olima.security.tenant.TenantResource;
import com.olima.security.tenant.TenantResourceResolver;
import com.olima.tool.ToolRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** tool resurslarining qaysi tashkilotga tegishliligini aniqlaydi (TenantAccess uchun). */
@Configuration
public class ToolTenantConfig {

  @Bean
  TenantResourceResolver toolTenantResolver(ToolRepository repository) {
    return TenantResourceResolver.of(TenantResource.TOOL, repository::findOrganizationIdById);
  }
}
