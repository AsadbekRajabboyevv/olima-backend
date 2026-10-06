package com.olima.agent.dto;

import java.util.UUID;

public record ConfirmActionContext(
    ConfirmActionRequest request, UUID scopeOrganizationId, ChatChannel channel) {}
