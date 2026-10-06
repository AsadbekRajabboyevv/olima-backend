package com.olima.agent.dto;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

public record PreparedTurn(
    ChatTurn turn,
    String issuedToken,
    String systemPrompt,
    List<Message> history,
    String userMessage) {}
