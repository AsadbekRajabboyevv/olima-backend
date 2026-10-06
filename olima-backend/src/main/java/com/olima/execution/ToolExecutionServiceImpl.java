package com.olima.execution;

import com.olima.execution.dto.ToolExecutionResponse;
import com.olima.execution.dto.ToolResult;
import com.olima.execution.enums.ExecutionStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionServiceImpl implements ToolExecutionService {

  private final ToolExecutionRepository repository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public void record(
      UUID orgId,
      UUID conversationId,
      UUID toolId,
      String toolName,
      Map<String, Object> input,
      ToolResult result,
      long durationMs) {
    try {
      ToolExecutionEntity entity =
          ToolExecutionEntity.builder()
              .organizationId(orgId)
              .conversationId(conversationId)
              .toolId(toolId)
              .toolName(toolName)
              .input(objectMapper.writeValueAsString(input))
              .output(result.data() != null ? objectMapper.writeValueAsString(result.data()) : null)
              .status(result.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED)
              .durationMs(durationMs)
              .errorMessage(result.error())
              .build();
      repository.save(entity);
    } catch (JacksonException e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw new RuntimeException("Failed to serialize execution data", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ToolExecutionResponse> findByOrganization(UUID orgId, Pageable pageable) {
    Page<ToolExecutionResponse> page =
        repository.findByOrganizationId(orgId, pageable).map(ToolExecutionResponse::of);
    return page;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ToolExecutionResponse> findByConversation(UUID conversationId) {
    List<ToolExecutionResponse> list =
        repository.findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
            .map(ToolExecutionResponse::of)
            .toList();
    return list;
  }
}
