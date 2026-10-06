package com.olima.agent;

import com.olima.agent.config.AgentProperties;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatStreamEvent;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.PreparedTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.agent.limiter.LlmConcurrencyLimiter;
import com.olima.common.util.MdcContext;
import com.olima.config.AgentExecutorConfig;
import com.olima.conversation.ConversationService;
import com.olima.conversation.enums.MessageRole;
import com.olima.usage.UsageService;
import com.olima.usage.dto.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class SseChatStreamer {

  private final LlmGateway llmGateway;
  private final ConversationService conversationService;
  private final UsageService usageService;
  private final ObjectMapper objectMapper;
  private final ExecutorService agentExecutor;
  private final AgentProperties properties;
  private final MeterRegistry meterRegistry;

  public SseChatStreamer(
      LlmGateway llmGateway,
      ConversationService conversationService,
      UsageService usageService,
      ObjectMapper objectMapper,
      @Qualifier(AgentExecutorConfig.AGENT_EXECUTOR) ExecutorService agentExecutor,
      AgentProperties properties,
      MeterRegistry meterRegistry) {
    this.llmGateway = llmGateway;
    this.conversationService = conversationService;
    this.usageService = usageService;
    this.objectMapper = objectMapper;
    this.agentExecutor = agentExecutor;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  public SseEmitter createEmitter() {
    return new SseEmitter(properties.streamTimeout().toMillis());
  }

  public void sendToolCall(SseEmitter emitter, ToolCallInfo info) {
    send(emitter, ChatStreamEvent.toolCall(info));
  }

  public void startStream(
      SseEmitter emitter,
      PreparedTurn prepared,
      ChatChannel channel,
      LlmConcurrencyLimiter.Permit permit) {
    Sinks.Empty<Void> cancelled = Sinks.empty();
    Runnable cancel = cancelled::tryEmitEmpty;
    emitter.onTimeout(cancel);
    emitter.onError(e -> cancel.run());
    emitter.onCompletion(cancel);

    UUID conversationId = prepared.turn().conversationId();
    Timer.Sample sample = Timer.start(meterRegistry);

    agentExecutor.execute(
        MdcContext.wrap(
            () -> {
              String outcome = "error";
              try {
                send(emitter, ChatStreamEvent.init(conversationId, prepared.issuedToken()));

                StringBuilder answer = new StringBuilder();
                AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.NONE);
                llmGateway
                    .streamWithFallback(prepared)
                    .takeUntilOther(cancelled.asMono())
                    .doOnNext(
                        response -> {
                          TokenUsage chunkUsage = llmGateway.usage(response);
                          if (!chunkUsage.isEmpty()) {
                            usage.set(chunkUsage);
                          }
                          String chunk = llmGateway.text(response);
                          if (!chunk.isEmpty()) {
                            answer.append(chunk);
                            send(emitter, ChatStreamEvent.content(chunk));
                          }
                        })
                    .blockLast();

                saveAssistantMessage(conversationId, answer.toString());
                recordUsage(prepared.turn(), usage.get());
                ChatTurn turn = prepared.turn();
                send(
                    emitter,
                    ChatStreamEvent.complete(
                        conversationId,
                        turn.sources(),
                        turn.confirmationRequired(),
                        turn.pendingComplaintId()));
                emitter.complete();
                outcome = "success";
              } catch (Exception e) {
                String ref = errorRef();
                log.error("Chat stream failed [ref={}, conversation={}]", ref, conversationId, e);
                send(
                    emitter,
                    ChatStreamEvent.error(
                        properties.messages().streamError().replace("{ref}", ref)));
                emitter.complete();
              } finally {
                permit.close();
                sample.stop(
                    meterRegistry.timer(
                        "olima.llm.request",
                        "channel",
                        channel.name(),
                        "mode",
                        "stream",
                        "outcome",
                        outcome));
              }
            }));
  }

  public void send(SseEmitter emitter, ChatStreamEvent event) {
    try {
      emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
    } catch (Exception e) {
      log.debug("Failed to send SSE event (client likely disconnected): {}", e.getMessage());
    }
  }

  private void saveAssistantMessage(UUID conversationId, String answer) {
    if (answer != null && !answer.isBlank()) {
      conversationService.addMessage(conversationId, MessageRole.ASSISTANT, answer, null, null);
    }
  }

  private void recordUsage(ChatTurn turn, TokenUsage usage) {
    try {
      usageService.record(
          turn.organizationId(), turn.conversationId(), turn.channel().name(), usage);
    } catch (Exception e) {
      log.warn(
          "Failed to record LLM usage for conversation {}: {}",
          turn.conversationId(),
          e.getMessage());
    }
  }

  private static String errorRef() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
