package com.olima.agent.factory;

import static com.olima.agent.config.AgentToolsProperties.render;
import static com.olima.agent.factory.AgentToolSupport.stringParam;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.execution.enums.ExecutionStatus;
import com.olima.knowledge.KnowledgeSearchService;
import com.olima.knowledge.dto.KnowledgeHit;
import com.olima.tool.util.ToolNames;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class KnowledgeSearchToolProvider implements AgentToolProvider {

  private static final String PARAM_QUERY = "query";

  private final KnowledgeSearchService knowledgeSearchService;
  private final AgentToolSupport support;
  private final AgentToolsProperties props;

  @Override
  public boolean supports(ChatTurn turn) {
    return true;
  }

  @Override
  public List<ToolCallback> create(ChatTurn turn) {
    return List.of(knowledgeSearchTool(turn));
  }

  private ToolCallback knowledgeSearchTool(ChatTurn turn) {
    AgentToolsProperties.Knowledge cfg = props.knowledge();
    String org = turn.organizationName();
    Function<Map<String, Object>, String> fn =
        params -> {
          long start = System.currentTimeMillis();
          try {
            String query = stringParam(params, PARAM_QUERY, "");
            List<KnowledgeHit> chunks =
                knowledgeSearchService.search(turn.organizationId(), query, cfg.maxResults());
            Map<UUID, String> labels = support.sourceLabels(chunks, org);

            List<Map<String, Object>> payload =
                chunks.stream()
                    .map(
                        c -> {
                          Map<String, Object> item = new LinkedHashMap<>();
                          item.put("content", c.content());
                          item.put("source", labels.get(c.id()));
                          return item;
                        })
                    .toList();
            turn.addSources(labels.values());

            String resultJson =
                payload.isEmpty()
                    ? support.toJson(
                        Map.of(
                            "results", List.of(), "message", render(cfg.emptyResultMessage(), org)))
                    : support.toJson(Map.of("results", payload));
            turn.recordToolCall(
                new ToolCallInfo(
                    ToolNames.SEARCH_KNOWLEDGE_BASE,
                    support.toJson(params),
                    resultJson,
                    ExecutionStatus.SUCCESS,
                    System.currentTimeMillis() - start));
            return resultJson;
          } catch (Exception e) {
            return support.failure(turn, ToolNames.SEARCH_KNOWLEDGE_BASE, params, start, e);
          }
        };

    return FunctionToolCallback.builder(ToolNames.SEARCH_KNOWLEDGE_BASE, fn)
        .description(render(cfg.description(), org))
        .inputType(Map.class)
        .inputSchema(support.stringParamSchema(PARAM_QUERY, render(cfg.queryDescription(), org)))
        .build();
  }
}
