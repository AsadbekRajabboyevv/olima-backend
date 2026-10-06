package com.olima.telegram.exception;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;

public class TelegramApiException extends BusinessException {

  public TelegramApiException(String message) {
    super(ErrorCode.TELEGRAM_API_ERROR, message);
  }

  public TelegramApiException(String message, Throwable cause) {
    super(ErrorCode.TELEGRAM_API_ERROR, message, cause);
  }
}
