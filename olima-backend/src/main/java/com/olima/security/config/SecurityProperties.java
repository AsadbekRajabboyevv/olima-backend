package com.olima.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull @DefaultValue Refresh refresh,
    @Valid @NotNull @DefaultValue Login login,
    @Valid @NotNull @DefaultValue Password password,
    @Valid @NotNull @DefaultValue BootstrapAdmin bootstrapAdmin,
    @Valid @NotNull @DefaultValue Cors cors,
    @NotNull @DefaultValue("30s") Duration sessionCacheTtl,
    @NotBlank String encryptionKey) {

  public record Jwt(
      @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
      @NotBlank @DefaultValue("olima") String issuer,
      @NotNull @DefaultValue("15m") Duration accessTokenTtl) {}

  public record Refresh(
      @NotNull @DefaultValue("14d") Duration ttl,
      @NotBlank @DefaultValue("olima_refresh") String cookieName,
      @NotBlank @DefaultValue("/api/v1/auth") String cookiePath,
      @DefaultValue("true") boolean cookieSecure,
      @NotBlank @DefaultValue("Strict") String cookieSameSite) {}

  public record Login(
      @Min(1) @DefaultValue("5") int maxFailures,
      @NotNull @DefaultValue("15m") Duration lockDuration) {}

  public record Password(
      @Min(6) @DefaultValue("10") int minLength,
      @DefaultValue("true") boolean requireLetterAndDigit) {}

  public record BootstrapAdmin(
      @DefaultValue("super") String username, @DefaultValue("") String password) {}

  public record Cors(@DefaultValue List<String> allowedOriginPatterns) {}
}
