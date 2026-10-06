package com.olima.integration.dto;

import com.olima.integration.config.AuthSettings;
import com.olima.integration.enums.AuthType;
import java.net.URI;
import java.util.UUID;

public record ConnectionSnapshot(
    UUID id,
    UUID organizationId,
    String name,
    URI baseUri,
    AuthType authType,
    AuthSettings settings,
    String username,
    String password,
    String secret) {

  @Override
  public String toString() {
    return "ConnectionSnapshot[name="
        + name
        + ", baseUri="
        + baseUri
        + ", authType="
        + authType
        + "]";
  }
}
