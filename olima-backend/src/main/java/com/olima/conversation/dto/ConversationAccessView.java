package com.olima.conversation.dto;

import java.util.UUID;

public interface ConversationAccessView {

  UUID getOrganizationId();

  String getAccessTokenHash();
}
