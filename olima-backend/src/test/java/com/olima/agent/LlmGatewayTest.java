package com.olima.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olima.agent.config.AgentProperties;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.PreparedTurn;
import com.olima.agent.factory.AgentToolFactory;
import com.olima.usage.dto.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

class LlmGatewayTest {

  private ChatClient.Builder chatClientBuilder;
  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private ChatClient.StreamResponseSpec streamResponseSpec;
  private AgentToolFactory toolFactory;
  private AgentProperties properties;
  private SimpleMeterRegistry meterRegistry;
  private LlmGateway gateway;

  @BeforeEach
  void setUp() {
    chatClientBuilder = mock(ChatClient.Builder.class);
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);
    toolFactory = mock(AgentToolFactory.class);
    properties = AgentTestProperties.defaults();
    meterRegistry = new SimpleMeterRegistry();

    when(chatClientBuilder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
    when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
    when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
    when(requestSpec.toolCallbacks(any(List.class))).thenReturn(requestSpec);
    when(requestSpec.options(any())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(requestSpec.stream()).thenReturn(streamResponseSpec);

    gateway = new LlmGateway(chatClientBuilder, toolFactory, properties, meterRegistry);
  }

  private PreparedTurn createPreparedTurn() {
    ChatTurn turn =
        new ChatTurn(UUID.randomUUID(), "Test Org", UUID.randomUUID(), ChatChannel.PANEL, false, null);
    return new PreparedTurn(turn, null, "system prompt", List.of(), "user question");
  }

  private ChatResponse mockChatResponse(String text, int promptTokens, int completionTokens, String model) {
    ChatResponse response = mock(ChatResponse.class);
    Generation generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text));
    when(response.getResult()).thenReturn(generation);

    ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
    Usage usage = mock(Usage.class);
    when(usage.getPromptTokens()).thenReturn(promptTokens);
    when(usage.getCompletionTokens()).thenReturn(completionTokens);
    when(metadata.getUsage()).thenReturn(usage);
    when(metadata.getModel()).thenReturn(model);
    when(response.getMetadata()).thenReturn(metadata);

    return response;
  }

  @Test
  void callWithFallback_successfulPrimary_returnsResponse() {
    PreparedTurn prepared = createPreparedTurn();
    ChatResponse response = mockChatResponse("Salom!", 15, 25, "primary-model");
    when(callResponseSpec.chatResponse()).thenReturn(response);

    ChatResponse result = gateway.callWithFallback(prepared);

    assertThat(gateway.text(result)).isEqualTo("Salom!");
    TokenUsage usage = gateway.usage(result);
    assertThat(usage.promptTokens()).isEqualTo(15);
    assertThat(usage.completionTokens()).isEqualTo(25);
    assertThat(usage.model()).isEqualTo("primary-model");
  }

  @Test
  void callWithFallback_primaryFails_retriesWithFallbackModel() {
    AgentProperties propsWithFallback =
        new AgentProperties(
            properties.maxConcurrentRequests(),
            properties.acquireTimeout(),
            properties.streamTimeout(),
            "fallback-model",
            properties.history(),
            properties.messages());

    LlmGateway gatewayWithFallback =
        new LlmGateway(chatClientBuilder, toolFactory, propsWithFallback, meterRegistry);

    PreparedTurn prepared = createPreparedTurn();
    ChatResponse fallbackResponse = mockChatResponse("Fallback javob", 10, 10, "fallback-model");

    // First call throws RuntimeException, second call succeeds
    when(callResponseSpec.chatResponse())
        .thenThrow(new RuntimeException("Primary model unavailable"))
        .thenReturn(fallbackResponse);

    ChatResponse result = gatewayWithFallback.callWithFallback(prepared);

    assertThat(gatewayWithFallback.text(result)).isEqualTo("Fallback javob");
    assertThat(meterRegistry.counter("olima.llm.fallback").count()).isEqualTo(1.0);
  }

  @Test
  void callWithFallback_noFallbackConfigured_throwsException() {
    AgentProperties noFallbackProps =
        new AgentProperties(
            properties.maxConcurrentRequests(),
            properties.acquireTimeout(),
            properties.streamTimeout(),
            null, // no fallback model
            properties.history(),
            properties.messages());

    LlmGateway gatewayNoFallback =
        new LlmGateway(chatClientBuilder, toolFactory, noFallbackProps, meterRegistry);

    PreparedTurn prepared = createPreparedTurn();
    when(callResponseSpec.chatResponse()).thenThrow(new RuntimeException("Fatal error"));

    assertThatThrownBy(() -> gatewayNoFallback.callWithFallback(prepared))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Fatal error");
  }

  @Test
  void streamWithFallback_emitsChunksSuccessfully() {
    PreparedTurn prepared = createPreparedTurn();
    ChatResponse chunk1 = mockChatResponse("Salom ", 0, 0, "stream-model");
    ChatResponse chunk2 = mockChatResponse("dunyo!", 10, 20, "stream-model");

    when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(chunk1, chunk2));

    Flux<ChatResponse> stream = gateway.streamWithFallback(prepared);

    List<ChatResponse> chunks = stream.collectList().block();
    assertThat(chunks).containsExactly(chunk1, chunk2);
  }
}
