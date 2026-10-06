package com.olima.common.http;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.http")
public record HttpClientProperties(
    @NotNull @DefaultValue("5s") Duration connectTimeout,
    @NotNull @DefaultValue("20s") Duration readTimeout,
    @NotNull @DefaultValue("2MB") DataSize maxResponseSize,
    @Min(0) @DefaultValue("5") int maxRedirects,
    @NotNull @DefaultValue("Mozilla/5.0 (compatible; OlimaBot/1.0)") String userAgent,
    @NotNull @DefaultValue Outbound outbound) {}
