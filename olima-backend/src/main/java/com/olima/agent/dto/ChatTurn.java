package com.olima.agent.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class ChatTurn {

  private final UUID organizationId;
  private final String organizationName;
  private final UUID conversationId;
  private final ChatChannel channel;

  private final boolean webSearchEnabled;
  private final Consumer<ToolCallInfo> toolCallListener;

  private final List<ToolCallInfo> toolCalls = new ArrayList<>();
  private final Set<String> sources = new LinkedHashSet<>();
  private UUID pendingComplaintId;

  public ChatTurn(
      UUID organizationId,
      String organizationName,
      UUID conversationId,
      ChatChannel channel,
      boolean webSearchEnabled,
      Consumer<ToolCallInfo> toolCallListener) {
    this.organizationId = organizationId;
    this.organizationName = organizationName;
    this.conversationId = conversationId;
    this.channel = channel;
    this.webSearchEnabled = webSearchEnabled;
    this.toolCallListener = toolCallListener != null ? toolCallListener : info -> {};
  }

  public UUID organizationId() {
    return organizationId;
  }

  public String organizationName() {
    return organizationName;
  }

  public UUID conversationId() {
    return conversationId;
  }

  public ChatChannel channel() {
    return channel;
  }

  public boolean webSearchEnabled() {
    return webSearchEnabled;
  }

  public void recordToolCall(ToolCallInfo info) {
    ToolCallInfo visible = channel.exposesToolDetails() ? info : info.withoutPayload();
    synchronized (this) {
      toolCalls.add(visible);
    }
    toolCallListener.accept(visible);
  }

  public synchronized void addSources(Collection<String> urls) {
    if (urls == null) {
      return;
    }
    for (String url : urls) {
      if (url != null && !url.isBlank()) {
        sources.add(url);
      }
    }
  }

  public synchronized void markPendingComplaint(UUID complaintId) {
    this.pendingComplaintId = complaintId;
  }

  public synchronized List<ToolCallInfo> toolCalls() {
    return List.copyOf(toolCalls);
  }

  public synchronized List<String> sources() {
    return List.copyOf(sources);
  }

  public synchronized UUID pendingComplaintId() {
    return pendingComplaintId;
  }

  public synchronized boolean confirmationRequired() {
    return pendingComplaintId != null;
  }
}
