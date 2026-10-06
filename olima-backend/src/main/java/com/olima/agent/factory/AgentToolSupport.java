package com.olima.agent.factory;

import static com.olima.agent.config.AgentToolsProperties.render;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.execution.dto.ToolResult;
import com.olima.execution.enums.ExecutionStatus;
import com.olima.knowledge.KnowledgeSearchService;
import com.olima.knowledge.dto.KnowledgeHit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolSupport {

  private final ObjectMapper objectMapper;
  private final AgentToolsProperties props;
  private final KnowledgeSearchService knowledgeSearchService;

  public Map<UUID, String> sourceLabels(List<KnowledgeHit> chunks, String orgName) {
    List<UUID> docIds =
        chunks.stream()
            .map(KnowledgeHit::documentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> titles = knowledgeSearchService.documentTitles(docIds);

    Map<UUID, String> labels = new LinkedHashMap<>();
    for (KnowledgeHit chunk : chunks) {
      labels.put(chunk.id(), sourceLabel(chunk, titles.get(chunk.documentId()), orgName));
    }
    return labels;
  }

  public String sourceLabel(KnowledgeHit chunk, String documentTitle, String orgName) {
    boolean hasOrg = orgName != null && !orgName.isBlank();
    String prefix = hasOrg ? orgName + ": " : "";
    String sourceUrl = chunk.sourceUrl();
    if (sourceUrl != null
        && (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://"))) {
      return sourceUrl;
    }
    if (documentTitle != null) {
      String title = cleanDocumentTitle(documentTitle);
      if (!title.isBlank()) {
        return prefix + title;
      }
    }
    String fromUrl = cleanDocumentTitle(sourceUrl);
    if (!fromUrl.isBlank()) {
      return prefix + fromUrl;
    }
    AgentToolsProperties.SourceLabels labels = props.sourceLabels();
    return hasOrg ? render(labels.organizationDocument(), orgName) : labels.genericDocument();
  }

  public String cleanDocumentTitle(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String title =
        raw.replaceAll("(?i)\\.[a-z0-9]{2,5}$", "")
            .replaceAll("^\\d{8,}[_\\-\\s]*", "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim();
    if (title.isBlank()
        || props.sourceLabels().genericTitles().stream().anyMatch(title::equalsIgnoreCase)) {
      return "";
    }
    return title.substring(0, 1).toUpperCase() + title.substring(1);
  }

  public boolean hasData(ToolResult result) {
    if (!result.success() || result.data() == null) {
      return false;
    }
    String str = result.data().toString().trim();
    return !str.isBlank() && !str.equals("null") && !str.equals("[]") && !str.equals("{}");
  }

  public String emptyResultInstruction(ChatTurn turn, ToolResult result) {
    Map<String, Object> fallback = new LinkedHashMap<>();
    fallback.put("success", false);
    fallback.put(
        "error", result.success() ? props.messages().noData() : props.messages().toolFailed());
    fallback.put(
        "next_step",
        turn.webSearchEnabled()
            ? props.messages().nextStepWithWebSearch()
            : props.messages().nextStepInternalOnly());
    if (!result.success()) {
      log.warn("Organization tool returned failure: {}", result.error());
    }
    return toJson(fallback);
  }

  public String failure(
      ChatTurn turn, String toolName, Map<String, Object> params, long start, Exception e) {
    log.error(
        "Tool '{}' failed in conversation {}: {}",
        toolName,
        turn.conversationId(),
        e.getMessage(),
        e);
    String json = toJson(Map.of("success", false, "error", props.messages().toolFailed()));
    turn.recordToolCall(
        new ToolCallInfo(
            toolName,
            toJson(params),
            json,
            ExecutionStatus.FAILED,
            System.currentTimeMillis() - start));
    return json;
  }

  public String stringParamSchema(String name, String description) {
    return toJson(
        Map.of(
            "type", "object",
            "properties", Map.of(name, Map.of("type", "string", "description", description)),
            "required", List.of(name)));
  }

  public static String stringParam(Map<String, Object> params, String key, String fallback) {
    Object value = params != null ? params.get(key) : null;
    return value != null ? String.valueOf(value) : fallback;
  }

  public String preview(String text) {
    if (text == null) {
      return "";
    }
    int max = props.toolCallPreviewChars();
    return text.length() > max ? text.substring(0, max) + "..." : text;
  }

  public String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      log.warn("Failed to serialize tool payload: {}", e.getMessage());
      return "{\"success\":false,\"error\":\"serialization failed\"}";
    }
  }
}
