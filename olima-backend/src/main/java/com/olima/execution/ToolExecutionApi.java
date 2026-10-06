package com.olima.execution;

import com.olima.execution.dto.ToolExecutionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/executions")
public interface ToolExecutionApi {

  @GetMapping(params = "organizationId")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<ToolExecutionResponse>> findByOrganization(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping(params = "conversationId")
  @PreAuthorize("@tenant.canAccess('CONVERSATION', #conversationId)")
  ResponseEntity<List<ToolExecutionResponse>> findByConversation(@RequestParam UUID conversationId);
}
