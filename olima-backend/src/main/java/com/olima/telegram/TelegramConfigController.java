package com.olima.telegram;

import com.olima.telegram.dto.TelegramBotConfigRequest;
import com.olima.telegram.dto.TelegramBotConfigResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telegram/config")
@RequiredArgsConstructor
public class TelegramConfigController implements TelegramConfigApi {

  private final TelegramService telegramService;

  @Override
  @GetMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<TelegramBotConfigResponse> getConfig(@RequestParam UUID organizationId) {
    TelegramBotConfigResponse config = telegramService.getConfig(organizationId);
    return config != null ? ResponseEntity.ok(config) : ResponseEntity.noContent().build();
  }

  @Override
  @PostMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<TelegramBotConfigResponse> saveConfig(
      @RequestParam UUID organizationId, @RequestBody @Valid TelegramBotConfigRequest request) {
    return ResponseEntity.ok(telegramService.configureBot(organizationId, request));
  }

  @Override
  @DeleteMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<Void> deleteConfig(@RequestParam UUID organizationId) {
    telegramService.disconnectBot(organizationId);
    return ResponseEntity.noContent().build();
  }
}
