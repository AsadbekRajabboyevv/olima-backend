package com.olima.knowledge.exception;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;

public class DocumentParseException extends BusinessException {

  public DocumentParseException(String message) {
    super(ErrorCode.DOCUMENT_PARSE_FAILED, message);
  }

  public DocumentParseException(String message, Throwable cause) {
    super(ErrorCode.DOCUMENT_PARSE_FAILED, message, cause);
  }
}
