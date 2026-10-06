package com.olima.agent.factory;

import com.olima.agent.dto.ChatTurn;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;

public interface AgentToolProvider {

  List<ToolCallback> create(ChatTurn turn);

  boolean supports(ChatTurn turn);
}
