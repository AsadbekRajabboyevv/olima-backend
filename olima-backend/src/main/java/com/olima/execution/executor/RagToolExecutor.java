package com.olima.execution.executor;

import com.olima.execution.dto.ToolResult;
import com.olima.knowledge.KnowledgeSearchService;
import com.olima.knowledge.dto.KnowledgeHit;
import com.olima.tool.ToolEntity;
import com.olima.tool.enums.ToolType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RagToolExecutor implements ToolExecutor {
  private static final int MAX_RESULTS = 10;

  private final KnowledgeSearchService knowledgeSearchService;
  private final ObjectMapper objectMapper;

  @Override
  public ToolType supportedType() {
    return ToolType.RAG;
  }

  @Override
  public ToolResult execute(ToolEntity tool, Map<String, Object> parameters) {
    try {
      String query = (String) parameters.get("query");
      if (query == null || query.isBlank()) {
        return ToolResult.failure("Query parameter is required");
      }
      List<KnowledgeHit> hits =
          knowledgeSearchService.search(tool.getOrganizationId(), query, MAX_RESULTS);
      List<String> sources =
          hits.stream()
              .map(KnowledgeHit::sourceUrl)
              .filter(RagToolExecutor::hasText)
              .distinct()
              .collect(Collectors.toList());
      // Modelga faqat matn va manba boradi — ichki ID va xizmat maydonlari emas
      List<Map<String, Object>> items = hits.stream().map(RagToolExecutor::toItem).toList();
      return ToolResult.success(items, sources);
    } catch (Exception e) {
      return ToolResult.failure(e.getMessage());
    }
  }

  private static Map<String, Object> toItem(KnowledgeHit hit) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("content", hit.content());
    if (hasText(hit.sourceUrl())) {
      item.put("source", hit.sourceUrl());
    }
    return item;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
