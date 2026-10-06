package com.olima.agent;

import com.olima.agent.dto.ChatContext;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatResponse;
import com.olima.agent.dto.ConfirmActionContext;
import com.olima.agent.dto.ConfirmActionRequest;
import com.olima.agent.resolver.AgentRequestResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AgentController implements AgentApi {

  private final AgentService agentService;
  private final AgentRequestResolver requestResolver;

  @Override
  public ResponseEntity<ChatResponse> chat(
      @RequestBody @Valid ChatRequest request, HttpServletRequest http) {
    ChatContext context = requestResolver.resolveChat(request, http);
    return ResponseEntity.ok(agentService.chat(context.request(), context.channel()));
  }

  @Override
  public SseEmitter chatStream(@RequestBody @Valid ChatRequest request, HttpServletRequest http) {
    ChatContext context = requestResolver.resolveChat(request, http);
    return agentService.chatStream(context.request(), context.channel());
  }

  @Override
  public ResponseEntity<ChatResponse> confirmAction(
      @RequestBody(required = false) ConfirmActionRequest body,
      @RequestParam(required = false) UUID conversationId,
      @RequestParam(required = false) UUID complaintId,
      @RequestParam(required = false) String conversationToken,
      HttpServletRequest http) {
    ConfirmActionContext context =
        requestResolver.resolveConfirm(body, conversationId, complaintId, conversationToken, http);
    return ResponseEntity.ok(
        agentService.confirmAction(
            context.request(), context.scopeOrganizationId(), context.channel()));
  }
}
