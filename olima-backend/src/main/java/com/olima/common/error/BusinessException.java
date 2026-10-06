package com.olima.common.error;

import java.time.Duration;

public class BusinessException extends RuntimeException {

  private final ErrorCode code;
  private final Duration retryAfter;

  public BusinessException(ErrorCode code, String message) {
    this(code, message, null, null);
  }

  public BusinessException(ErrorCode code, String message, Throwable cause) {
    this(code, message, cause, null);
  }

  public BusinessException(ErrorCode code, String message, Throwable cause, Duration retryAfter) {
    super(message, cause);
    this.code = code;
    this.retryAfter = retryAfter;
  }

  public ErrorCode code() {
    return code;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
