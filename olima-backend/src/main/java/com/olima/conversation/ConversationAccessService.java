package com.olima.conversation;

import java.util.UUID;

public interface ConversationAccessService {

  String newAccessToken();

  String hash(String token);

  void verify(UUID conversationId, UUID organizationId, String accessToken, boolean requireToken);
}
