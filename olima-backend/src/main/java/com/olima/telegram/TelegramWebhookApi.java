package com.olima.telegram;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/telegram")
public interface TelegramWebhookApi {

  String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

  @PostMapping("/webhook/{webhookSecret}")
  ResponseEntity<Void> receiveUpdate(
      @PathVariable String webhookSecret,
      @RequestHeader(name = SECRET_HEADER, required = false) String headerSecret,
      @RequestBody String rawBody);
}
