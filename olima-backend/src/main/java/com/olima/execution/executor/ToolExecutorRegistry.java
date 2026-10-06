package com.olima.execution.executor;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.execution.dto.ToolResult;
import com.olima.tool.ToolEntity;
import com.olima.tool.enums.ToolType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutorRegistry {

  private final Map<ToolType, ToolExecutor> executors = new EnumMap<>(ToolType.class);

  public ToolExecutorRegistry(List<ToolExecutor> executors) {
    for (ToolExecutor executor : executors) {
      if (this.executors.put(executor.supportedType(), executor) != null) {
        throw new IllegalStateException("Duplicate executor for " + executor.supportedType());
      }
    }
  }

  public Set<ToolType> supportedTypes() {
    return Set.copyOf(executors.keySet());
  }

  public boolean supports(ToolType type) {
    return executors.containsKey(type);
  }

  public ToolExecutor getExecutor(ToolType type) {
    ToolExecutor executor = executors.get(type);
    if (executor == null) {
      throw new BusinessException(
          ErrorCode.UNSUPPORTED_TOOL_TYPE, "Tool type is not supported: " + type);
    }
    return executor;
  }

  public ToolResult execute(ToolEntity tool, Map<String, Object> params) {
    return getExecutor(tool.getType()).execute(tool, params);
  }
}
