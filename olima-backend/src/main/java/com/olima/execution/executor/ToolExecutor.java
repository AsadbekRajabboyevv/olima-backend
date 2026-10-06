package com.olima.execution.executor;

import com.olima.execution.dto.ToolResult;
import com.olima.tool.ToolEntity;
import com.olima.tool.enums.ToolType;
import java.util.Map;

public interface ToolExecutor {
  ToolResult execute(ToolEntity tool, Map<String, Object> parameters);

  ToolType supportedType();
}
