package com.olima.agent.dto;

public enum ChatChannel {
  WIDGET,
  PANEL,
  TELEGRAM;

  public boolean requiresConversationToken() {
    return this == WIDGET;
  }

  public boolean exposesToolDetails() {
    return this != WIDGET;
  }
}
