package com.olima.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

  private RequestLoggingFilter filter;

  @BeforeEach
  void setUp() {
    AppLoggingProperties properties = new AppLoggingProperties(Duration.ofSeconds(3));
    filter = new RequestLoggingFilter(properties);
  }

  @Test
  void doFilter_normalGet_logsSingleInfoLineWithPattern(CapturedOutput output)
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/organizations/123");
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/organizations/{id}");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(output.getOut())
        .contains("HTTP GET /api/v1/organizations/{id} -> 200")
        .doesNotContain("WARN")
        .doesNotContain("SLOW");
  }

  @Test
  void doFilter_500Status_logsWarn(CapturedOutput output) throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/chat");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(500);

    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(output.getOut()).contains("WARN").contains("HTTP POST /api/v1/chat -> 500");
  }

  @Test
  void doFilter_telegramWebhook_masksSecret(CapturedOutput output)
      throws ServletException, IOException {
    String sensitiveSecret = "my-super-secret-token-xyz123";
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/telegram/webhook/" + sensitiveSecret);
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(output.getOut())
        .contains("HTTP POST /api/v1/telegram/webhook/{secret} -> 200")
        .doesNotContain(sensitiveSecret);
  }

  @Test
  void doFilter_queryString_neverLogged(CapturedOutput output)
      throws ServletException, IOException {
    String sensitiveToken = "secret-token-value-999";
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/knowledge");
    request.setQueryString("token=" + sensitiveToken + "&filter=test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(output.getOut())
        .contains("HTTP GET /api/v1/knowledge -> 200")
        .doesNotContain(sensitiveToken)
        .doesNotContain("filter=test");
  }

  @Test
  void doFilter_slowRequest_logsWarnAndSlow(CapturedOutput output)
      throws ServletException, IOException {
    // Configured with 50ms threshold
    RequestLoggingFilter slowFilter =
        new RequestLoggingFilter(new AppLoggingProperties(Duration.ofMillis(50)));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/heavy");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest req, ServletResponse res) {
            try {
              Thread.sleep(70);
            } catch (InterruptedException ignored) {
            }
          }
        };

    slowFilter.doFilter(request, response, chain);

    assertThat(output.getOut())
        .contains("WARN")
        .contains("HTTP GET /api/v1/heavy -> 200")
        .contains("SLOW");
  }

  @Test
  void shouldNotFilter_actuatorEndpoints() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    assertThat(filter.shouldNotFilter(request)).isTrue();

    MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/v1/organizations");
    assertThat(filter.shouldNotFilter(apiRequest)).isFalse();
  }

  @Test
  void doFilter_asyncStarted_defersLoggingUntilAsyncComplete(CapturedOutput output)
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/chat/stream");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest req, ServletResponse res) {
            request.setAsyncStarted(true);
            request.setAsyncContext(new MockAsyncContext(req, res));
          }
        };

    filter.doFilter(request, response, chain);

    // Not logged yet because async is in progress
    assertThat(output.getOut()).doesNotContain("HTTP POST /api/v1/chat/stream");

    // Complete the async context
    AsyncContext asyncContext = request.getAsyncContext();
    for (AsyncListener listener : ((MockAsyncContext) asyncContext).getListeners()) {
      listener.onComplete(new jakarta.servlet.AsyncEvent(asyncContext));
    }

    assertThat(output.getOut()).contains("HTTP POST /api/v1/chat/stream -> 200");
  }
}
