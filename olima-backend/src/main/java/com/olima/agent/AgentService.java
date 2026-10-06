package com.olima.agent;

import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatResponse;
import com.olima.agent.dto.ConfirmActionRequest;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentService {

  ChatResponse chat(ChatRequest request, ChatChannel channel);

  SseEmitter chatStream(ChatRequest request, ChatChannel channel);

  ChatResponse confirmAction(
      ConfirmActionRequest request, UUID scopeOrganizationId, ChatChannel channel);
}
