package com.olima.common.error;

public class NotFoundException extends BusinessException {

  public NotFoundException(String message) {
    super(ErrorCode.NOT_FOUND, message);
  }

  public NotFoundException(ErrorCode code, String message) {
    super(code, message);
  }
}
