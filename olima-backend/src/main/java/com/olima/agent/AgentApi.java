package com.olima.agent;

import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatResponse;
import com.olima.agent.dto.ConfirmActionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequestMapping("/api/v1")
public interface AgentApi {

  @PostMapping("/chat")
  ResponseEntity<ChatResponse> chat(
      @RequestBody @Valid ChatRequest request, HttpServletRequest http);

  @PostMapping("/chat/stream")
  SseEmitter chatStream(@RequestBody @Valid ChatRequest request, HttpServletRequest http);

  @PostMapping("/chat/confirm")
  ResponseEntity<ChatResponse> confirmAction(
      @RequestBody(required = false) ConfirmActionRequest body,
      @RequestParam(required = false) UUID conversationId,
      @RequestParam(required = false) UUID complaintId,
      @RequestParam(required = false) String conversationToken,
      HttpServletRequest http);
}
