package com.olima.complaint.config;

import com.olima.complaint.ComplaintRepository;
import com.olima.security.tenant.TenantResource;
import com.olima.security.tenant.TenantResourceResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** complaint resurslarining qaysi tashkilotga tegishliligini aniqlaydi (TenantAccess uchun). */
@Configuration
public class ComplaintTenantConfig {

  @Bean
  TenantResourceResolver complaintTenantResolver(ComplaintRepository repository) {
    return TenantResourceResolver.of(TenantResource.COMPLAINT, repository::findOrganizationIdById);
  }
}
