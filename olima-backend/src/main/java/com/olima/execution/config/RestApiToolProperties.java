package com.olima.execution.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.tools.rest-api")
public record RestApiToolProperties(
    @DefaultValue("15s") Duration defaultTimeout,
    @DefaultValue("30s") Duration maxTimeout,
    @DefaultValue("512KB") DataSize maxResponseSize,
    @Min(256) @DefaultValue("16000") int maxResponseChars,
    @NotEmpty @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE"}) List<String> allowedMethods,
    @DefaultValue({
          "host",
          "content-length",
          "connection",
          "transfer-encoding",
          "upgrade",
          "expect"
        })
        List<String> forbiddenHeaders) {}
