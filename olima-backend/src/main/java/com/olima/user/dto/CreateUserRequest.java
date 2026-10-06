package com.olima.user.dto;

import com.olima.user.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUserRequest(
    @NotBlank @Size(max = 100) String username,
    @NotBlank @Size(max = 128) String password,
    @NotNull UserRole role,
    UUID organizationId) {}
