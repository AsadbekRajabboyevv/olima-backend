package com.olima.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UrlIngestRequest(
    @NotBlank @Size(max = 1000) String url, @Size(max = 500) String title) {}
