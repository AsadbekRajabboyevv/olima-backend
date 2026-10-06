package com.olima.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled, @Valid @DefaultValue List<Rule> rules) {

  public enum KeyType {
    IP,
    ORGANIZATION,
    USER
  }

  public record Rule(
      @NotBlank String name,
      @NotEmpty List<String> paths,
      @DefaultValue List<String> methods,
      @NotNull @DefaultValue("IP") KeyType key,
      @Min(1) int capacity,
      @NotNull Duration period) {}
}
