package com.olima.tool.dto;

import java.util.UUID;

public record ToolParameterResponse(
    UUID id, String name, String type, String description, boolean required, String defaultValue) {}
