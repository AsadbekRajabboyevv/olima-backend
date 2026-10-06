package com.olima.common.web;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Pattern TELEGRAM_WEBHOOK_PATTERN =
      Pattern.compile("^(/api/v1/telegram/webhook)/[^/?#]+(.*)$");

  private final AppLoggingProperties properties;

  public RequestLoggingFilter(AppLoggingProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    return path.startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (request.isAsyncStarted()) {
        AsyncContext asyncContext = request.getAsyncContext();
        final Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        final AtomicBoolean logged = new AtomicBoolean(false);
        asyncContext.addListener(
            new AsyncListener() {
              @Override
              public void onComplete(AsyncEvent event) {
                logAsync(request, response, startTime, mdcContext, logged);
              }

              @Override
              public void onTimeout(AsyncEvent event) {
                logAsync(request, response, startTime, mdcContext, logged);
              }

              @Override
              public void onError(AsyncEvent event) {
                logAsync(request, response, startTime, mdcContext, logged);
              }

              @Override
              public void onStartAsync(AsyncEvent event) {}
            });
      } else {
        logRequest(request, response, startTime);
      }
    }
  }

  private void logAsync(
      HttpServletRequest request,
      HttpServletResponse response,
      long startTime,
      Map<String, String> mdcContext,
      AtomicBoolean logged) {
    if (!logged.compareAndSet(false, true)) {
      return;
    }
    Map<String, String> previous = MDC.getCopyOfContextMap();
    if (mdcContext != null) {
      MDC.setContextMap(mdcContext);
    } else {
      MDC.clear();
    }
    try {
      logRequest(request, response, startTime);
    } finally {
      if (previous != null) {
        MDC.setContextMap(previous);
      } else {
        MDC.clear();
      }
    }
  }

  private void logRequest(
      HttpServletRequest request, HttpServletResponse response, long startTime) {
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
    String method = request.getMethod();
    String pattern =
        (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String path = pattern != null ? pattern : request.getRequestURI();
    path = sanitize(path);

    int status = response.getStatus();
    long thresholdMs =
        properties != null && properties.slowRequestThreshold() != null
            ? properties.slowRequestThreshold().toMillis()
            : 3000L;
    boolean isSlow = durationMs >= thresholdMs;

    String logMessage =
        String.format(
            "HTTP %s %s -> %d (%d ms)%s",
            method, path, status, durationMs, isSlow ? " SLOW" : "");

    if (status >= 500 || isSlow) {
      log.warn(logMessage);
    } else {
      log.info(logMessage);
    }
  }

  private String sanitize(String path) {
    if (path == null) {
      return "";
    }
    var matcher = TELEGRAM_WEBHOOK_PATTERN.matcher(path);
    if (matcher.matches()) {
      String suffix = matcher.group(2);
      return matcher.group(1) + "/{secret}" + (suffix != null ? suffix : "");
    }
    return path;
  }
}
