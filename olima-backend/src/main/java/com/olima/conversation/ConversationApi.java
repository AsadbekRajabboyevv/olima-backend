package com.olima.conversation;

import com.olima.conversation.dto.ConversationResponse;
import com.olima.conversation.dto.ConversationSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/conversations")
public interface ConversationApi {

  @GetMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<ConversationSummary>> findByOrganization(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping("/{id}")
  @PreAuthorize("@tenant.canAccess('CONVERSATION', #id)")
  ResponseEntity<ConversationResponse> findById(@PathVariable UUID id);
}
