package com.olima.agent.builder;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.organization.dto.OrganizationResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class SystemPromptBuilder {

  private static final String DEFAULT_DESCRIPTION =
      "No further description was provided by the organization.";

  private final String template;
  private final AgentToolsProperties.SourcePolicy sourcePolicy;

  public SystemPromptBuilder(
      @Value("${app.agent.system-prompt:classpath:prompts/system-prompt.md}") Resource resource,
      AgentToolsProperties props) {
    try {
      this.template = resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("System prompt template could not be read: " + resource, e);
    }
    this.sourcePolicy = props.sourcePolicy();
  }

  public String build(OrganizationResponse org, boolean webSearchEnabled) {
    String description =
        (org.description() != null && !org.description().isBlank())
            ? org.description().trim()
            : DEFAULT_DESCRIPTION;
    String policy = webSearchEnabled ? sourcePolicy.withWebSearch() : sourcePolicy.internalOnly();
    return template
        .replace("{{sourcePolicy}}", AgentToolsProperties.render(policy, org.name()))
        .replace("{{orgName}}", org.name())
        .replace("{{orgDescription}}", description);
  }
}
