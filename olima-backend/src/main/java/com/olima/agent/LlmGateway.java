package com.olima.agent;

import com.olima.agent.config.AgentProperties;
import com.olima.agent.dto.PreparedTurn;
import com.olima.agent.factory.AgentToolFactory;
import com.olima.usage.dto.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGateway {

  private final ChatClient.Builder chatClientBuilder;
  private final AgentToolFactory toolFactory;
  private final AgentProperties properties;
  private final MeterRegistry meterRegistry;

  public ChatResponse callWithFallback(PreparedTurn prepared) {
    try {
      return prompt(prepared, null, false).call().chatResponse();
    } catch (RuntimeException e) {
      String fallback = fallbackModel();
      if (fallback == null) {
        throw e;
      }
      log.warn(
          "Primary model failed ({}), retrying with fallback model {}", e.getMessage(), fallback);
      meterRegistry.counter("olima.llm.fallback").increment();
      return prompt(prepared, fallback, false).call().chatResponse();
    }
  }

  public Flux<ChatResponse> streamWithFallback(PreparedTurn prepared) {
    AtomicBoolean emitted = new AtomicBoolean(false);
    Flux<ChatResponse> primary =
        prompt(prepared, null, true).stream().chatResponse().doOnNext(r -> emitted.set(true));
    String fallback = fallbackModel();
    if (fallback == null) {
      return primary;
    }

    return primary.onErrorResume(
        e -> !emitted.get(),
        e -> {
          log.warn(
              "Primary model stream failed ({}), retrying with fallback model {}",
              e.getMessage(),
              fallback);
          meterRegistry.counter("olima.llm.fallback").increment();
          return prompt(prepared, fallback, true).stream().chatResponse();
        });
  }

  public ChatClient.ChatClientRequestSpec prompt(
      PreparedTurn prepared, String modelOverride, boolean streaming) {
    ChatClient.ChatClientRequestSpec spec =
        chatClientBuilder
            .build()
            .prompt()
            .system(prepared.systemPrompt())
            .messages(prepared.history())
            .user(prepared.userMessage())
            .toolCallbacks(toolFactory.build(prepared.turn()));
    if (streaming || modelOverride != null) {
      OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
      if (streaming) {
        options.streamUsage(true);
      }
      if (modelOverride != null) {
        options.model(modelOverride);
      }
      spec = spec.options(options);
    }
    return spec;
  }

  public String text(ChatResponse response) {
    if (response == null
        || response.getResult() == null
        || response.getResult().getOutput() == null) {
      return "";
    }
    String text = response.getResult().getOutput().getText();
    return text != null ? text : "";
  }

  public TokenUsage usage(ChatResponse response) {
    if (response == null) {
      return TokenUsage.NONE;
    }
    ChatResponseMetadata metadata = response.getMetadata();
    if (metadata == null || metadata.getUsage() == null) {
      return TokenUsage.NONE;
    }
    Usage usage = metadata.getUsage();
    int prompt = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
    int completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
    return new TokenUsage(prompt, completion, metadata.getModel());
  }

  private String fallbackModel() {
    String fallback = properties.fallbackModel();
    return fallback == null || fallback.isBlank() ? null : fallback.trim();
  }
}
