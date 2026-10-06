package com.olima.common.web;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.logging")
public record AppLoggingProperties(
    @NotNull @DefaultValue("3s") Duration slowRequestThreshold) {

  public AppLoggingProperties() {
    this(Duration.ofSeconds(3));
  }
}
