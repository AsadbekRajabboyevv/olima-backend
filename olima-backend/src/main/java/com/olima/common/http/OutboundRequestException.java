package com.olima.common.http;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;

public class OutboundRequestException extends BusinessException {

  public OutboundRequestException(String message) {
    super(ErrorCode.OUTBOUND_REQUEST_REJECTED, message);
  }

  public OutboundRequestException(String message, Throwable cause) {
    super(ErrorCode.OUTBOUND_REQUEST_REJECTED, message, cause);
  }
}
