package com.olima.agent.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatTurn;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.complaint.ComplaintService;
import com.olima.execution.ToolExecutionService;
import com.olima.execution.WebSearchService;
import com.olima.execution.executor.ToolExecutorRegistry;
import com.olima.knowledge.KnowledgeSearchService;
import com.olima.tool.ToolEntity;
import com.olima.tool.enums.ToolType;
import com.olima.tool.registry.DynamicToolCallbackFactory;
import com.olima.tool.registry.ToolRegistry;
import com.olima.tool.util.ToolNames;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;

class AgentToolFactoryTest {

  private ToolRegistry toolRegistry;
  private ToolExecutorRegistry toolExecutorRegistry;
  private DynamicToolCallbackFactory dynamicToolCallbackFactory;
  private ToolExecutionService toolExecutionService;
  private ComplaintService complaintService;
  private KnowledgeSearchService knowledgeSearchService;
  private WebSearchService webSearchService;
  private OutboundUrlPolicy outboundUrlPolicy;
  private AgentToolsProperties props;

  private UUID orgId;

  @BeforeEach
  void setUp() {
    toolRegistry = mock(ToolRegistry.class);
    toolExecutorRegistry = mock(ToolExecutorRegistry.class);
    dynamicToolCallbackFactory = mock(DynamicToolCallbackFactory.class);
    toolExecutionService = mock(ToolExecutionService.class);
    complaintService = mock(ComplaintService.class);
    knowledgeSearchService = mock(KnowledgeSearchService.class);
    webSearchService = mock(WebSearchService.class);
    outboundUrlPolicy = mock(OutboundUrlPolicy.class);

    props =
        new AgentToolsProperties(
            new AgentToolsProperties.Knowledge(5, "Search {org}", "Query for {org}", "No result {org}"),
            new AgentToolsProperties.WebSearch(true, "Web search", "Web query"),
            new AgentToolsProperties.FetchPage(true, "Fetch url", "URL param", "Blocked"),
            new AgentToolsProperties.Complaint("General", "Draft created", "pending", "Support"),
            new AgentToolsProperties.ModelMessages("Failed", "No data", "Try web", "Internal only"),
            new AgentToolsProperties.SourceLabels("Doc {org}", "Generic doc", List.of("doc", "file")),
            new AgentToolsProperties.SourcePolicy("withWebSearch", "internalOnly"),
            120);

    orgId = UUID.randomUUID();
  }

  private AgentToolFactory createFactory(AgentToolsProperties testProps) {
    AgentToolSupport support =
        new AgentToolSupport(new ObjectMapper(), testProps, knowledgeSearchService);

    OrganizationToolsProvider orgProvider =
        new OrganizationToolsProvider(
            toolRegistry,
            toolExecutorRegistry,
            dynamicToolCallbackFactory,
            toolExecutionService,
            complaintService,
            support,
            testProps);

    KnowledgeSearchToolProvider knowledgeProvider =
        new KnowledgeSearchToolProvider(knowledgeSearchService, support, testProps);

    WebSearchToolProvider webProvider =
        new WebSearchToolProvider(webSearchService, support, testProps);

    FetchWebPageToolProvider fetchProvider =
        new FetchWebPageToolProvider(webSearchService, outboundUrlPolicy, support, testProps);

    return new AgentToolFactory(
        List.of(orgProvider, knowledgeProvider, webProvider, fetchProvider), testProps);
  }

  private ChatTurn turn(boolean webSearch) {
    return new ChatTurn(orgId, "Olima Test Org", UUID.randomUUID(), ChatChannel.PANEL, webSearch, null);
  }

  private org.springframework.ai.tool.function.FunctionToolCallback realCallback(String name) {
    return org.springframework.ai.tool.function.FunctionToolCallback.builder(
            name, (Map<String, Object> p) -> "ok")
        .description("desc")
        .inputType(Map.class)
        .build();
  }

  @Test
  void build_strictOrdering_orgToolsThenKnowledgeThenWebThenFetch() {
    ToolEntity dbTool =
        ToolEntity.builder().name("get_user_info").type(ToolType.REST_API).organizationId(orgId).build();
    dbTool.setId(UUID.randomUUID());

    when(toolRegistry.getEnabledTools(orgId)).thenReturn(List.of(dbTool));
    when(toolExecutorRegistry.supports(ToolType.REST_API)).thenReturn(true);
    when(dynamicToolCallbackFactory.createCallback(eq(dbTool), any()))
        .thenReturn(realCallback("get_user_info"));

    AgentToolFactory factory = createFactory(props);
    List<ToolCallback> callbacks = factory.build(turn(true));

    List<String> names = callbacks.stream().map(c -> c.getToolDefinition().name()).toList();
    assertThat(names)
        .containsExactly(
            "get_user_info",
            ToolNames.SEARCH_KNOWLEDGE_BASE,
            ToolNames.SEARCH_INTERNET,
            ToolNames.FETCH_WEB_PAGE);
  }

  @Test
  void build_whenWebSearchDisabled_excludesWebSearchAndFetchPage() {
    when(toolRegistry.getEnabledTools(orgId)).thenReturn(List.of());

    AgentToolFactory factory = createFactory(props);
    List<ToolCallback> callbacks = factory.build(turn(false));

    List<String> names = callbacks.stream().map(c -> c.getToolDefinition().name()).toList();
    assertThat(names).containsExactly(ToolNames.SEARCH_KNOWLEDGE_BASE);
  }

  @Test
  void build_whenFetchPageDisabledInProps_excludesFetchPageEvenIfWebSearchEnabled() {
    when(toolRegistry.getEnabledTools(orgId)).thenReturn(List.of());

    AgentToolsProperties noFetchProps =
        new AgentToolsProperties(
            props.knowledge(),
            props.webSearch(),
            new AgentToolsProperties.FetchPage(false, "Fetch url", "URL param", "Blocked"),
            props.complaint(),
            props.messages(),
            props.sourceLabels(),
            props.sourcePolicy(),
            props.toolCallPreviewChars());

    AgentToolFactory factory = createFactory(noFetchProps);
    List<ToolCallback> callbacks = factory.build(turn(true));

    List<String> names = callbacks.stream().map(c -> c.getToolDefinition().name()).toList();
    assertThat(names).containsExactly(ToolNames.SEARCH_KNOWLEDGE_BASE, ToolNames.SEARCH_INTERNET);
  }

  @Test
  void webSearchAllowed_checksPlatformAndOrgFlag() {
    AgentToolFactory factory = createFactory(props);

    assertThat(factory.webSearchAllowed(true)).isTrue();
    assertThat(factory.webSearchAllowed(false)).isFalse();

    AgentToolsProperties webOffProps =
        new AgentToolsProperties(
            props.knowledge(),
            new AgentToolsProperties.WebSearch(false, "desc", "query"),
            props.fetchPage(),
            props.complaint(),
            props.messages(),
            props.sourceLabels(),
            props.sourcePolicy(),
            props.toolCallPreviewChars());

    AgentToolFactory factoryWebOff = createFactory(webOffProps);
    assertThat(factoryWebOff.webSearchAllowed(true)).isFalse();
    assertThat(factoryWebOff.webSearchAllowed(false)).isFalse();
  }
}
