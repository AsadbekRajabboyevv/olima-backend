package com.olima.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olima.agent.builder.SystemPromptBuilder;
import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.factory.AgentToolFactory;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.complaint.ComplaintService;
import com.olima.execution.ToolExecutionService;
import com.olima.execution.WebSearchService;
import com.olima.execution.executor.ToolExecutorRegistry;
import com.olima.knowledge.KnowledgeSearchService;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.tool.registry.DynamicToolCallbackFactory;
import com.olima.tool.registry.ToolRegistry;
import com.olima.tool.util.ToolNames;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class AgentToolsPropertiesTest {

  private static AgentToolsProperties loadDefaults() throws Exception {
    var sources =
        new YamlPropertySourceLoader()
            .load("agent-tools", new ClassPathResource("agent-tools.yml"));
    return new Binder(ConfigurationPropertySources.from(sources))
        .bind("app.agent.tools", AgentToolsProperties.class)
        .get();
  }

  @Test
  void defaultsBindAndPassValidation() throws Exception {
    AgentToolsProperties props = loadDefaults();

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(props)).isEmpty();
    }
    assertThat(props.knowledge().maxResults()).isEqualTo(5);
    assertThat(props.knowledge().description()).contains(AgentToolsProperties.ORG_PLACEHOLDER);
    assertThat(props.sourceLabels().genericTitles()).contains("fayl", "document");
    assertThat(props.webSearch().enabled()).isTrue();
  }

  private static AgentToolFactory factory(AgentToolsProperties props) {
    ToolRegistry registry = mock(ToolRegistry.class);
    when(registry.getEnabledTools(any())).thenReturn(List.of());
    KnowledgeSearchService knowledge = mock(KnowledgeSearchService.class);
    com.olima.agent.factory.AgentToolSupport support =
        new com.olima.agent.factory.AgentToolSupport(new ObjectMapper(), props, knowledge);

    var orgProvider =
        new com.olima.agent.factory.OrganizationToolsProvider(
            registry,
            mock(ToolExecutorRegistry.class),
            mock(DynamicToolCallbackFactory.class),
            mock(ToolExecutionService.class),
            mock(ComplaintService.class),
            support,
            props);

    var knowledgeProvider =
        new com.olima.agent.factory.KnowledgeSearchToolProvider(knowledge, support, props);

    WebSearchService webSearchService = mock(WebSearchService.class);
    var webProvider =
        new com.olima.agent.factory.WebSearchToolProvider(webSearchService, support, props);

    var fetchProvider =
        new com.olima.agent.factory.FetchWebPageToolProvider(
            webSearchService, mock(OutboundUrlPolicy.class), support, props);

    return new AgentToolFactory(
        List.of(orgProvider, knowledgeProvider, webProvider, fetchProvider), props);
  }

  private static ChatTurn turn(boolean webSearch) {
    return new ChatTurn(
        UUID.randomUUID(),
        "Test Universiteti",
        UUID.randomUUID(),
        ChatChannel.PANEL,
        webSearch,
        null);
  }

  private static List<String> toolNames(List<ToolCallback> callbacks) {
    return callbacks.stream().map(c -> c.getToolDefinition().name()).toList();
  }

  @Test
  void organizationWithoutWebSearchGetsOnlyInternalSources() throws Exception {
    List<ToolCallback> callbacks = factory(loadDefaults()).build(turn(false));

    assertThat(toolNames(callbacks)).containsExactly(ToolNames.SEARCH_KNOWLEDGE_BASE);
    assertThat(callbacks.get(0).getToolDefinition().description())
        .contains("Test Universiteti")
        .doesNotContain(AgentToolsProperties.ORG_PLACEHOLDER);
  }

  @Test
  void organizationWithWebSearchGetsInternetTools() throws Exception {
    List<ToolCallback> callbacks = factory(loadDefaults()).build(turn(true));

    assertThat(toolNames(callbacks))
        .containsExactly(
            ToolNames.SEARCH_KNOWLEDGE_BASE, ToolNames.SEARCH_INTERNET, ToolNames.FETCH_WEB_PAGE);
  }

  @Test
  void platformSwitchOverridesOrganizationSetting() throws Exception {
    AgentToolsProperties defaults = loadDefaults();
    AgentToolsProperties platformOff =
        new AgentToolsProperties(
            defaults.knowledge(),
            new AgentToolsProperties.WebSearch(
                false, defaults.webSearch().description(), defaults.webSearch().queryDescription()),
            defaults.fetchPage(),
            defaults.complaint(),
            defaults.messages(),
            defaults.sourceLabels(),
            defaults.sourcePolicy(),
            defaults.toolCallPreviewChars());

    assertThat(factory(platformOff).webSearchAllowed(true)).isFalse();
    assertThat(factory(defaults).webSearchAllowed(true)).isTrue();
    assertThat(factory(defaults).webSearchAllowed(false)).isFalse();
  }

  @Test
  void systemPromptMatchesWebSearchSetting() throws Exception {
    SystemPromptBuilder builder =
        new SystemPromptBuilder(new ClassPathResource("prompts/system-prompt.md"), loadDefaults());
    OrganizationResponse org =
        new OrganizationResponse(
            UUID.randomUUID(),
            "Test Universiteti",
            "test",
            "Xususiy universitet",
            true,
            "wk_x",
            null,
            true,
            false,
            null,
            null,
            null);

    String internalOnly = builder.build(org, false);
    assertThat(internalOnly)
        .contains("NO internet access", "Test Universiteti")
        .doesNotContain("search_internet", "fetch_web_page", "{{", "{org}");

    String withWeb = builder.build(org, true);
    assertThat(withWeb)
        .contains("search_internet", "fetch_web_page")
        .doesNotContain("NO internet access", "{{", "{org}");
  }
}
