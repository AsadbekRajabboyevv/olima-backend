package com.olima.conversation.config;

import com.olima.conversation.ConversationRepository;
import com.olima.security.tenant.TenantResource;
import com.olima.security.tenant.TenantResourceResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** conversation resurslarining qaysi tashkilotga tegishliligini aniqlaydi (TenantAccess uchun). */
@Configuration
public class ConversationTenantConfig {

  @Bean
  TenantResourceResolver conversationTenantResolver(ConversationRepository repository) {
    return TenantResourceResolver.of(
        TenantResource.CONVERSATION, repository::findOrganizationIdById);
  }
}
