package com.olima.integration.dto;

import com.olima.integration.config.AuthSettings;
import com.olima.integration.enums.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConnectionRequest(
    @NotBlank
        @Pattern(
            regexp = "^[a-z0-9_-]{1,64}$",
            message = "faqat kichik lotin harflari, raqam, _ va -")
        String name,
    @Size(max = 1000) String description,
    @NotBlank @Size(max = 1000) String baseUrl,
    @NotNull AuthType authType,
    AuthSettings authConfig,
    @Size(max = 500) String username,
    @Size(max = 500) String password,
    @Size(max = 3000) String secret,
    Boolean enabled) {

  @Override
  public String toString() {
    return "ConnectionRequest[name="
        + name
        + ", baseUrl="
        + baseUrl
        + ", authType="
        + authType
        + "]";
  }
}
