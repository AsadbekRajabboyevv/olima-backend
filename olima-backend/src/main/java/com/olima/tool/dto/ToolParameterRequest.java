package com.olima.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ToolParameterRequest(
    @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_]{1,64}$", message = "faqat lotin harflari, raqam va _")
        String name,
    @NotBlank String type,
    String description,
    Boolean required,
    String defaultValue) {}
