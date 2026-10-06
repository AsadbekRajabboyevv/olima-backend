package com.olima.security.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.olima.common.error.ErrorCode;
import com.olima.common.error.ProblemResponses;
import com.olima.security.filter.WidgetKeyFilter;
import com.olima.security.model.AuthenticatedUser;
import com.olima.security.tenant.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPatternParser;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final boolean enabled;
  private final List<CompiledRule> rules;
  private final ProblemResponses problems;
  private final MeterRegistry meterRegistry;

  public RateLimitFilter(
      RateLimitProperties properties, ProblemResponses problems, MeterRegistry meterRegistry) {
    this.enabled = properties.enabled();
    this.problems = problems;
    this.meterRegistry = meterRegistry;
    PathPatternParser parser = PathPatternParser.defaultInstance;
    this.rules =
        properties.rules().stream()
            .map(
                rule ->
                    new CompiledRule(
                        rule,
                        rule.paths().stream().map(parser::parse).toList(),
                        Caffeine.newBuilder()
                            .expireAfterAccess(rule.period().multipliedBy(2))
                            .maximumSize(100_000)
                            .<String, TokenBucket>build()))
            .toList();
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (enabled && !"OPTIONS".equalsIgnoreCase(request.getMethod())) {
      PathContainer path = PathContainer.parsePath(request.getRequestURI());
      for (CompiledRule compiled : rules) {
        if (!applies(compiled, request, path)) {
          continue;
        }
        String key = key(compiled.rule().key(), request);
        if (key == null) {
          continue;
        }
        long waitNanos =
            compiled
                .buckets()
                .get(
                    key, k -> new TokenBucket(compiled.rule().capacity(), compiled.rule().period()))
                .tryConsume();
        if (waitNanos > 0) {
          meterRegistry
              .counter("olima.ratelimit.rejected", "rule", compiled.rule().name())
              .increment();
          log.debug("Rate limit '{}' exceeded for {}", compiled.rule().name(), key);
          problems.write(
              response,
              ErrorCode.RATE_LIMITED,
              "Juda ko'p so'rov. Birozdan keyin qayta urinib ko'ring",
              Duration.ofNanos(waitNanos));
          return;
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  private static boolean applies(
      CompiledRule compiled, HttpServletRequest request, PathContainer path) {
    List<String> methods = compiled.rule().methods();
    if (!methods.isEmpty()
        && methods.stream().noneMatch(m -> m.equalsIgnoreCase(request.getMethod()))) {
      return false;
    }
    return compiled.patterns().stream().anyMatch(p -> p.matches(path));
  }

  private static String key(RateLimitProperties.KeyType type, HttpServletRequest request) {
    return switch (type) {
      case IP -> "ip:" + request.getRemoteAddr();
      case USER -> CurrentUser.get().map(u -> "user:" + u.userId()).orElse(null);
      case ORGANIZATION -> {
        Object widgetOrg = request.getAttribute(WidgetKeyFilter.WIDGET_ORG_ATTR);
        if (widgetOrg instanceof UUID id) {
          yield "org:" + id;
        }
        yield CurrentUser.get()
            .map(AuthenticatedUser::organizationId)
            .map(id -> "org:" + id.toString().toLowerCase(Locale.ROOT))
            .orElse(null);
      }
    };
  }
}
