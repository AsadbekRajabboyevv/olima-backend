package com.olima.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ProblemResponses {

  private static final String TYPE_PREFIX = "urn:olima:error:";

  private final ObjectMapper objectMapper;

  public ProblemDetail build(ErrorCode code, String detail) {
    return build(code, code.status(), detail);
  }

  public ProblemDetail build(ErrorCode code, HttpStatus status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_PREFIX + code.name().toLowerCase(Locale.ROOT)));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code.name());
    problem.setProperty("message", detail);
    problem.setProperty("timestamp", Instant.now().toString());
    return problem;
  }

  public void write(
      HttpServletResponse response, ErrorCode code, String detail, Duration retryAfter)
      throws IOException {
    HttpStatus status = code.status();
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    if (retryAfter != null) {
      response.setHeader(
          HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
    }
    ProblemDetail problem = build(code, detail);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", problem.getType().toString());
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    body.put("detail", problem.getDetail());
    if (problem.getProperties() != null) {
      body.putAll(problem.getProperties());
    }
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }

  public void write(HttpServletResponse response, ErrorCode code, String detail)
      throws IOException {
    write(response, code, detail, null);
  }
}
