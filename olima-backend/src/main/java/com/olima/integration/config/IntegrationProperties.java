package com.olima.integration.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.integration")
public record IntegrationProperties(
    @NotNull @DefaultValue("30m") Duration defaultTokenTtl,
    @NotNull @DefaultValue("60s") Duration tokenRefreshSkew,
    @NotNull @DefaultValue("15s") Duration loginTimeout,
    @Min(1) @DefaultValue("10000") int maxCachedTokens) {}
