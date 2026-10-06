package com.olima.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController implements TelegramWebhookApi {

  private final TelegramService telegramService;

  @Override
  @PostMapping("/webhook/{webhookSecret}")
  public ResponseEntity<Void> receiveUpdate(
      @PathVariable String webhookSecret,
      @RequestHeader(name = SECRET_HEADER, required = false) String headerSecret,
      @RequestBody String rawBody) {
    telegramService.processWebhookAsync(webhookSecret, headerSecret, rawBody);
    return ResponseEntity.ok().build();
  }
}
