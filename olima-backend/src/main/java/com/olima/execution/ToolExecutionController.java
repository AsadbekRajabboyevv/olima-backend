package com.olima.execution;

import com.olima.common.web.Paging;
import com.olima.execution.dto.ToolExecutionResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
public class ToolExecutionController implements ToolExecutionApi {

  private final ToolExecutionService service;
  private final Paging paging;

  @Override
  @GetMapping(params = "organizationId")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<ToolExecutionResponse>> findByOrganization(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return Paging.ok(
        service.findByOrganization(
            organizationId, paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @Override
  @GetMapping(params = "conversationId")
  @PreAuthorize("@tenant.canAccess('CONVERSATION', #conversationId)")
  public ResponseEntity<List<ToolExecutionResponse>> findByConversation(
      @RequestParam UUID conversationId) {
    return ResponseEntity.ok(service.findByConversation(conversationId));
  }
}
