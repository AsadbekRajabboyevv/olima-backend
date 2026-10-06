package com.olima.usage.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.quota")
public record QuotaProperties(
    @DefaultValue("-1") long defaultMonthlyTokens,
    @NotNull @DefaultValue("30s") Duration usageCacheTtl,
    @NotNull @DefaultValue("Asia/Tashkent") ZoneId zone) {}
