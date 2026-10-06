package com.olima.agent.factory;

import static com.olima.agent.config.AgentToolsProperties.render;
import static com.olima.agent.factory.AgentToolSupport.stringParam;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.execution.WebSearchService;
import com.olima.execution.dto.SearchResult;
import com.olima.execution.enums.ExecutionStatus;
import com.olima.tool.util.ToolNames;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
public class WebSearchToolProvider implements AgentToolProvider {

  private static final String PARAM_QUERY = "query";

  private final WebSearchService webSearchService;
  private final AgentToolSupport support;
  private final AgentToolsProperties props;

  @Override
  public boolean supports(ChatTurn turn) {
    return turn.webSearchEnabled();
  }

  @Override
  public List<ToolCallback> create(ChatTurn turn) {
    return List.of(webSearchTool(turn));
  }

  private ToolCallback webSearchTool(ChatTurn turn) {
    AgentToolsProperties.WebSearch cfg = props.webSearch();
    String org = turn.organizationName();
    Function<Map<String, Object>, String> fn =
        params -> {
          long start = System.currentTimeMillis();
          try {
            String query = stringParam(params, PARAM_QUERY, "");
            List<SearchResult> results = webSearchService.search(query);
            turn.addSources(results.stream().map(SearchResult::url).toList());

            String resultJson = support.toJson(results);
            turn.recordToolCall(
                new ToolCallInfo(
                    ToolNames.SEARCH_INTERNET,
                    support.toJson(params),
                    resultJson,
                    ExecutionStatus.SUCCESS,
                    System.currentTimeMillis() - start));
            return resultJson;
          } catch (Exception e) {
            return support.failure(turn, ToolNames.SEARCH_INTERNET, params, start, e);
          }
        };

    return FunctionToolCallback.builder(ToolNames.SEARCH_INTERNET, fn)
        .description(render(cfg.description(), org))
        .inputType(Map.class)
        .inputSchema(support.stringParamSchema(PARAM_QUERY, render(cfg.queryDescription(), org)))
        .build();
  }
}
