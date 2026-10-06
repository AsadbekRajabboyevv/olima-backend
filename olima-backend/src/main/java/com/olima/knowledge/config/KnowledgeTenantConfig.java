package com.olima.knowledge.config;

import com.olima.knowledge.DocumentRepository;
import com.olima.knowledge.KnowledgeBaseRepository;
import com.olima.security.tenant.TenantResource;
import com.olima.security.tenant.TenantResourceResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** knowledge resurslarining qaysi tashkilotga tegishliligini aniqlaydi (TenantAccess uchun). */
@Configuration
public class KnowledgeTenantConfig {

  @Bean
  TenantResourceResolver knowledgeBaseTenantResolver(KnowledgeBaseRepository repository) {
    return TenantResourceResolver.of(
        TenantResource.KNOWLEDGE_BASE, repository::findOrganizationIdById);
  }

  @Bean
  TenantResourceResolver documentTenantResolver(DocumentRepository repository) {
    return TenantResourceResolver.of(TenantResource.DOCUMENT, repository::findOrganizationIdById);
  }
}
