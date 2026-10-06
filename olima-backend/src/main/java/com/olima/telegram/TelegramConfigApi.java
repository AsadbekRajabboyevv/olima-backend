package com.olima.telegram;

import com.olima.telegram.dto.TelegramBotConfigRequest;
import com.olima.telegram.dto.TelegramBotConfigResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/telegram/config")
public interface TelegramConfigApi {

  @GetMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<TelegramBotConfigResponse> getConfig(@RequestParam UUID organizationId);

  @PostMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<TelegramBotConfigResponse> saveConfig(
      @RequestParam UUID organizationId, @RequestBody @Valid TelegramBotConfigRequest request);

  @DeleteMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<Void> deleteConfig(@RequestParam UUID organizationId);
}
