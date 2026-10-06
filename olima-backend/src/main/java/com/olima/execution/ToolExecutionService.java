package com.olima.execution;

import com.olima.execution.dto.ToolExecutionResponse;
import com.olima.execution.dto.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ToolExecutionService {

  void record(
      UUID orgId,
      UUID conversationId,
      UUID toolId,
      String toolName,
      Map<String, Object> input,
      ToolResult result,
      long durationMs);

  Page<ToolExecutionResponse> findByOrganization(UUID orgId, Pageable pageable);

  List<ToolExecutionResponse> findByConversation(UUID conversationId);
}
