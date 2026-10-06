package com.olima.agent.factory;

import com.olima.agent.config.AgentToolsProperties;
import com.olima.agent.dto.ChatTurn;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolFactory {

  private final List<AgentToolProvider> providers;
  private final AgentToolsProperties props;

  public List<ToolCallback> build(ChatTurn turn) {
    List<ToolCallback> callbacks = new ArrayList<>();
    for (AgentToolProvider provider : providers) {
      if (provider.supports(turn)) {
        callbacks.addAll(provider.create(turn));
      }
    }
    return callbacks;
  }

  public boolean webSearchAllowed(boolean organizationSetting) {
    return props.webSearch().enabled() && organizationSetting;
  }
}
