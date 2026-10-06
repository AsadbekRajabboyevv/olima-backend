package com.olima.integration.exception;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;

public class IntegrationAuthException extends BusinessException {

  public IntegrationAuthException(String message) {
    super(ErrorCode.INTEGRATION_AUTH_FAILED, message);
  }
}
