package com.olima.execution.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ToolResult(
    boolean success,
    Object data,
    List<String> sources,
    String error,
    Map<String, Object> metadata) {
  public static ToolResult success(Object data) {
    return new ToolResult(true, data, Collections.emptyList(), null, Collections.emptyMap());
  }

  public static ToolResult success(Object data, List<String> sources) {
    return new ToolResult(true, data, sources, null, Collections.emptyMap());
  }

  public static ToolResult failure(String error) {
    return new ToolResult(false, null, Collections.emptyList(), error, Collections.emptyMap());
  }
}
