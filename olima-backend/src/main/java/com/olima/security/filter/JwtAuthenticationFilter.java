package com.olima.security.filter;

import com.olima.security.JwtService;
import com.olima.security.model.AuthenticatedUser;
import com.olima.security.model.ParsedToken;
import com.olima.security.validator.UserSessionValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";
  private static final String ROLE = "ROLE_";

  private final JwtService jwtService;
  private final UserSessionValidator sessionValidator;

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
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (header != null && header.startsWith(BEARER)) {
      String token = header.substring(BEARER.length());
      try {
        ParsedToken parsed = jwtService.parseToken(token);
        AuthenticatedUser user = parsed.user();
        if (sessionValidator.isActive(user.userId(), parsed.version())) {
          var authorities = List.of(new SimpleGrantedAuthority(ROLE + user.role().name()));
          var authToken = new UsernamePasswordAuthenticationToken(user, null, authorities);
          SecurityContextHolder.getContext().setAuthentication(authToken);
          MDC.put("userId", user.userId().toString());
          if (user.organizationId() != null) {
            MDC.put("orgId", user.organizationId().toString());
          }
        } else {
          log.debug("Rejected revoked token of user {}", user.userId());
          SecurityContextHolder.clearContext();
        }
      } catch (Exception e) {
        log.debug("Invalid JWT token: {}", e.getMessage());
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }
}
