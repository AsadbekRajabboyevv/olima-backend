package com.olima.conversation;

import com.olima.common.web.Paging;
import com.olima.conversation.dto.ConversationResponse;
import com.olima.conversation.dto.ConversationSummary;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController implements ConversationApi {

  private final ConversationService conversationService;
  private final Paging paging;

  @Override
  @GetMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<ConversationSummary>> findByOrganization(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return Paging.ok(
        conversationService.findByOrganization(
            organizationId, paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @Override
  @GetMapping("/{id}")
  @PreAuthorize("@tenant.canAccess('CONVERSATION', #id)")
  public ResponseEntity<ConversationResponse> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(conversationService.findById(id));
  }
}
