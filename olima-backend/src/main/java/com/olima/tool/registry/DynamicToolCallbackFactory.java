package com.olima.tool.registry;

import com.olima.tool.ToolEntity;
import com.olima.tool.ToolParameterEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicToolCallbackFactory {

  private final ObjectMapper objectMapper;

  public FunctionToolCallback createCallback(
      ToolEntity tool, Function<Map<String, Object>, String> executionFunction) {
    String inputSchema = buildInputSchema(tool);

    Function<Map<String, Object>, String> wrapper =
        params -> {
          try {
            Map<String, Object> finalParams = params != null ? params : Collections.emptyMap();
            return executionFunction.apply(finalParams);
          } catch (Exception e) {

            log.error("Failed to execute tool {}", tool.getName(), e);
            return "{\"success\": false, \"error\": \"Tool execution failed\"}";
          }
        };

    return FunctionToolCallback.builder(tool.getName(), wrapper)
        .description(tool.getDescription())
        .inputType(Map.class)
        .inputSchema(inputSchema)
        .build();
  }

  private String buildInputSchema(ToolEntity tool) {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");

    Map<String, Object> properties = new HashMap<>();
    List<String> required = new ArrayList<>();

    if (tool.getParameters() != null) {
      for (ToolParameterEntity param : tool.getParameters()) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("type", param.getType());
        if (param.getDescription() != null) {
          prop.put("description", param.getDescription());
        }
        properties.put(param.getName(), prop);
        if (param.isRequired()) {
          required.add(param.getName());
        }
      }
    }

    schema.put("properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }

    try {
      return objectMapper.writeValueAsString(schema);
    } catch (JacksonException e) {
      return "{\"type\":\"object\",\"properties\":{}}";
    }
  }
}
