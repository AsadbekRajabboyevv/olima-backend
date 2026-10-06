package com.olima.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextDocumentRequest(
    @NotBlank @Size(max = 500) String title, @NotBlank String content) {}
