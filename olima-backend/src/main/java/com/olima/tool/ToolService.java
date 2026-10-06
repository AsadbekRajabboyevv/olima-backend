package com.olima.tool;

import com.olima.tool.dto.ToolRequest;
import com.olima.tool.dto.ToolResponse;
import com.olima.tool.enums.ToolType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ToolService {

  List<ToolType> getSupportedTypes();

  List<ToolResponse> findByOrganization(UUID orgId);

  ToolResponse findById(UUID id);

  ToolResponse create(UUID orgId, ToolRequest request);

  ToolResponse update(UUID id, ToolRequest request);

  void delete(UUID id);

  ToolResponse toggleEnabled(UUID id, boolean enabled);

  ToolResponse toggleEnabled(UUID id, Boolean enabled, Map<String, Object> body);
}
