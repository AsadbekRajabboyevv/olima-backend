package com.olima.integration.config;

import com.olima.integration.IntegrationConnectionRepository;
import com.olima.security.tenant.TenantResource;
import com.olima.security.tenant.TenantResourceResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** integration resurslarining qaysi tashkilotga tegishliligini aniqlaydi (TenantAccess uchun). */
@Configuration
public class IntegrationTenantConfig {

  @Bean
  TenantResourceResolver connectionTenantResolver(IntegrationConnectionRepository repository) {
    return TenantResourceResolver.of(TenantResource.CONNECTION, repository::findOrganizationIdById);
  }
}
