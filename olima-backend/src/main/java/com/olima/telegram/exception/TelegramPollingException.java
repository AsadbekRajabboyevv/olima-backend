package com.olima.telegram.exception;

/**
 * getUpdates muvaffaqiyatsiz tugadi. Poller buni qayta urinish bilan hal qiladi, shuning uchun
 * BusinessException emas va stack trace bilan loglanmaydi.
 *
 * @param errorCode Telegram'ning error_code (409 — webhook o'rnatilgan yoki boshqa getUpdates
 *     ishlayapti, 401 — token bekor qilingan); tarmoq xatosida 0
 */
public class TelegramPollingException extends RuntimeException {

  private final int errorCode;

  public TelegramPollingException(int errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public int errorCode() {
    return errorCode;
  }
}
