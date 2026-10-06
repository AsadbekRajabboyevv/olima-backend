package com.olima.user.exception;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;

public class DuplicateUsernameException extends BusinessException {

  public DuplicateUsernameException(String message) {
    super(ErrorCode.DUPLICATE_USERNAME, message);
  }

  public DuplicateUsernameException(String message, Throwable cause) {
    super(ErrorCode.DUPLICATE_USERNAME, message, cause);
  }
}
