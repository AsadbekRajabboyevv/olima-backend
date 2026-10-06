package com.olima.common.web;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.pagination")
public record PaginationProperties(
    @Min(1) @DefaultValue("100") int defaultSize, @Min(1) @DefaultValue("500") int maxSize) {}
