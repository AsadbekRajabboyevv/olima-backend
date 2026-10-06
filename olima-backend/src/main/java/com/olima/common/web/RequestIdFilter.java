package com.olima.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    String requestId =
        incoming != null && SAFE.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    response.setHeader(HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
