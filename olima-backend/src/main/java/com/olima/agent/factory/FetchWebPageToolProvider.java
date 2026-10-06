package com.olima.agent.factory;

import static com.olima.agent.config.AgentToolsProperties.render;
import static com.olima.agent.factory.AgentToolSupport.stringParam;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.execution.WebSearchService;
import com.olima.execution.enums.ExecutionStatus;
import com.olima.tool.util.ToolNames;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class FetchWebPageToolProvider implements AgentToolProvider {

  private static final String PARAM_URL = "url";

  private final WebSearchService webSearchService;
  private final OutboundUrlPolicy outboundUrlPolicy;
  private final AgentToolSupport support;
  private final AgentToolsProperties props;

  @Override
  public boolean supports(ChatTurn turn) {
    return turn.webSearchEnabled() && props.fetchPage().enabled();
  }

  @Override
  public List<ToolCallback> create(ChatTurn turn) {
    return List.of(fetchWebPageTool(turn));
  }

  private ToolCallback fetchWebPageTool(ChatTurn turn) {
    AgentToolsProperties.FetchPage cfg = props.fetchPage();
    String org = turn.organizationName();
    Function<Map<String, Object>, String> fn =
        params -> {
          long start = System.currentTimeMillis();
          String url = stringParam(params, PARAM_URL, "");

          if (!outboundUrlPolicy.isAllowed(url)) {
            log.warn("Blocked fetch_web_page for non-public url: {}", url);
            String blocked = support.toJson(Map.of("success", false, "error", cfg.blockedMessage()));
            turn.recordToolCall(
                new ToolCallInfo(
                    ToolNames.FETCH_WEB_PAGE,
                    support.toJson(params),
                    blocked,
                    ExecutionStatus.FAILED,
                    System.currentTimeMillis() - start));
            return blocked;
          }
          try {
            String content = webSearchService.fetchPage(url);
            turn.addSources(List.of(url));
            turn.recordToolCall(
                new ToolCallInfo(
                    ToolNames.FETCH_WEB_PAGE,
                    support.toJson(params),
                    support.preview(content),
                    ExecutionStatus.SUCCESS,
                    System.currentTimeMillis() - start));
            return content;
          } catch (Exception e) {
            return support.failure(turn, ToolNames.FETCH_WEB_PAGE, params, start, e);
          }
        };

    return FunctionToolCallback.builder(ToolNames.FETCH_WEB_PAGE, fn)
        .description(render(cfg.description(), org))
        .inputType(Map.class)
        .inputSchema(support.stringParamSchema(PARAM_URL, render(cfg.urlDescription(), org)))
        .build();
  }
}
