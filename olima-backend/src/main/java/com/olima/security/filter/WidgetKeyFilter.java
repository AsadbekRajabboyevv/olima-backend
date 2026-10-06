package com.olima.security.filter;

import com.olima.common.error.ErrorCode;
import com.olima.common.error.ProblemResponses;
import com.olima.organization.OrganizationEntity;
import com.olima.organization.OrganizationRepository;
import com.olima.security.model.AuthenticatedUser;
import com.olima.user.enums.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class WidgetKeyFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Widget-Key";
  public static final String PARAM = "widgetKey";
  public static final String WIDGET_ORG_ATTR = "olima.widget.organizationId";
  private static final String CHAT_PREFIX = "/api/v1/chat";

  private final OrganizationRepository organizationRepository;
  private final ProblemResponses problems;

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (!request.getRequestURI().startsWith(CHAT_PREFIX)
        || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    var existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing != null
        && existing.isAuthenticated()
        && existing.getPrincipal() instanceof AuthenticatedUser) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = request.getHeader(HEADER);
    if (key == null || key.isBlank()) {
      key = request.getParameter(PARAM);
    }
    if (key == null || key.isBlank()) {
      problems.write(
          response,
          ErrorCode.WIDGET_KEY_REQUIRED,
          "Chat so'rovi uchun " + HEADER + " sarlavhasi kerak");
      return;
    }

    Optional<OrganizationEntity> org =
        organizationRepository.findByWidgetKeyAndEnabledTrue(key.trim());
    if (org.isEmpty()) {
      problems.write(
          response, ErrorCode.INVALID_WIDGET_KEY, "Kalit topilmadi yoki tashkilot o'chirilgan");
      return;
    }

    UUID orgId = org.get().getId();
    var principal =
        new AuthenticatedUser(orgId, "widget:" + org.get().getSlug(), UserRole.WIDGET, orgId);
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + UserRole.WIDGET.name())));
    SecurityContextHolder.getContext().setAuthentication(auth);
    MDC.put("orgId", orgId.toString());

    request.setAttribute(WIDGET_ORG_ATTR, orgId);
    filterChain.doFilter(request, response);
  }
}
