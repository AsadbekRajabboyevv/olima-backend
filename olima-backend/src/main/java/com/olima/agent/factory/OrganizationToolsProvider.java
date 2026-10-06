package com.olima.agent.factory;

import static com.olima.agent.factory.AgentToolSupport.stringParam;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.complaint.ComplaintService;
import com.olima.complaint.dto.ComplaintResponse;
import com.olima.execution.ToolExecutionService;
import com.olima.execution.dto.ToolResult;
import com.olima.execution.enums.ExecutionStatus;
import com.olima.execution.executor.ToolExecutorRegistry;
import com.olima.tool.ToolEntity;
import com.olima.tool.registry.DynamicToolCallbackFactory;
import com.olima.tool.registry.ToolRegistry;
import com.olima.tool.util.ToolNames;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class OrganizationToolsProvider implements AgentToolProvider {

  private static final String PARAM_SUBJECT = "subject";
  private static final String PARAM_DESCRIPTION = "description";
  private static final String PARAM_CATEGORY = "category";

  private final ToolRegistry toolRegistry;
  private final ToolExecutorRegistry toolExecutorRegistry;
  private final DynamicToolCallbackFactory dynamicToolCallbackFactory;
  private final ToolExecutionService toolExecutionService;
  private final ComplaintService complaintService;
  private final AgentToolSupport support;
  private final AgentToolsProperties props;

  @Override
  public boolean supports(ChatTurn turn) {
    return true;
  }

  @Override
  public List<ToolCallback> create(ChatTurn turn) {
    List<ToolCallback> callbacks = new ArrayList<>();
    for (ToolEntity tool : toolRegistry.getEnabledTools(turn.organizationId())) {
      if (!toolExecutorRegistry.supports(tool.getType())) {
        log.debug("Skipping tool '{}': type {} has no executor", tool.getName(), tool.getType());
        continue;
      }
      if (!ToolNames.isValid(tool.getName()) || ToolNames.isReserved(tool.getName())) {
        log.warn(
            "Skipping tool '{}' of organization {}: invalid or reserved name",
            tool.getName(),
            turn.organizationId());
        continue;
      }
      callbacks.add(
          dynamicToolCallbackFactory.createCallback(
              tool, params -> executeOrganizationTool(turn, tool, params)));
    }
    return callbacks;
  }

  private String executeOrganizationTool(
      ChatTurn turn, ToolEntity tool, Map<String, Object> params) {
    long start = System.currentTimeMillis();
    try {
      if (tool.isRequiresConfirmation()) {
        return createComplaintDraft(turn, tool, params, start);
      }

      ToolResult result = toolExecutorRegistry.execute(tool, params);
      long duration = System.currentTimeMillis() - start;
      toolExecutionService.record(
          turn.organizationId(),
          turn.conversationId(),
          tool.getId(),
          tool.getName(),
          params,
          result,
          duration);
      turn.addSources(result.sources());

      boolean hasData = support.hasData(result);
      String resultJson = hasData ? support.toJson(result.data()) : support.emptyResultInstruction(turn, result);
      turn.recordToolCall(
          new ToolCallInfo(
              tool.getName(),
              support.toJson(params),
              resultJson,
              hasData ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED,
              duration));
      return resultJson;
    } catch (Exception e) {
      return support.failure(turn, tool.getName(), params, start, e);
    }
  }

  private String createComplaintDraft(
      ChatTurn turn, ToolEntity tool, Map<String, Object> params, long start) {
    AgentToolsProperties.Complaint cfg = props.complaint();
    String subject = stringParam(params, PARAM_SUBJECT, cfg.defaultSubject());
    String description = stringParam(params, PARAM_DESCRIPTION, "");
    String category = stringParam(params, PARAM_CATEGORY, cfg.defaultCategory());

    ComplaintResponse draft =
        complaintService.createDraft(
            turn.organizationId(), turn.conversationId(), subject, description, category);
    turn.markPendingComplaint(draft.id());

    turn.recordToolCall(
        new ToolCallInfo(
            tool.getName(),
            support.toJson(params),
            cfg.toolCallStatus(),
            ExecutionStatus.WAITING_CONFIRMATION,
            System.currentTimeMillis() - start));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", ExecutionStatus.WAITING_CONFIRMATION.name());
    result.put(PARAM_SUBJECT, subject);
    result.put("message", cfg.draftCreatedMessage());
    return support.toJson(result);
  }
}
