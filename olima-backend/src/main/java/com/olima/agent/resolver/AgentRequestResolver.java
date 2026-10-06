package com.olima.agent.resolver;

import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatContext;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ConfirmActionContext;
import com.olima.agent.dto.ConfirmActionRequest;
import com.olima.security.filter.WidgetKeyFilter;
import com.olima.security.model.AuthenticatedUser;
import com.olima.user.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AgentRequestResolver {

  public ChatContext resolveChat(ChatRequest request, HttpServletRequest http) {
    ChatChannel channel = resolveChannel(http);
    ChatRequest scopedRequest = resolveScopedRequest(request, http);
    return new ChatContext(scopedRequest, channel);
  }

  public ConfirmActionContext resolveConfirm(
      ConfirmActionRequest body,
      UUID conversationId,
      UUID complaintId,
      String conversationToken,
      HttpServletRequest http) {

    ConfirmActionRequest request =
        new ConfirmActionRequest(
            body != null && body.conversationId() != null ? body.conversationId() : conversationId,
            body != null && body.complaintId() != null ? body.complaintId() : complaintId,
            body != null && body.conversationToken() != null
                ? body.conversationToken()
                : conversationToken);

    UUID scopedOrgId = resolveScopeOrganizationId(http);
    ChatChannel channel = resolveChannel(http);

    return new ConfirmActionContext(request, scopedOrgId, channel);
  }

  public ChatChannel resolveChannel(HttpServletRequest http) {
    return resolveWidgetOrganizationId(http) != null ? ChatChannel.WIDGET : ChatChannel.PANEL;
  }

  public ChatRequest resolveScopedRequest(ChatRequest request, HttpServletRequest http) {
    UUID widgetOrgId = resolveWidgetOrganizationId(http);
    if (widgetOrgId != null) {
      return widgetOrgId.equals(request.organizationId())
          ? request
          : request.withOrganizationId(widgetOrgId);
    }
    if (request.organizationId() == null) {
      throw new IllegalArgumentException(
          "organizationId is required when no widget key is supplied");
    }
    return request;
  }

  public UUID resolveScopeOrganizationId(HttpServletRequest http) {
    UUID widgetOrgId = resolveWidgetOrganizationId(http);
    if (widgetOrgId != null) {
      return widgetOrgId;
    }
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new AccessDeniedException("Authentication is required");
    }
    return user.role() == UserRole.SUPER_ADMIN ? null : user.organizationId();
  }

  public UUID resolveWidgetOrganizationId(HttpServletRequest http) {
    return http.getAttribute(WidgetKeyFilter.WIDGET_ORG_ATTR) instanceof UUID id ? id : null;
  }
}
