package com.olima.auth.exception;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;

public class InvalidCredentialsException extends BusinessException {

  public InvalidCredentialsException(String message) {
    super(ErrorCode.INVALID_CREDENTIALS, message);
  }

  public InvalidCredentialsException(String message, Throwable cause) {
    super(ErrorCode.INVALID_CREDENTIALS, message, cause);
  }
}
