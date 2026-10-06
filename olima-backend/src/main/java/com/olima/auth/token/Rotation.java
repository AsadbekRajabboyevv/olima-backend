package com.olima.auth.token;

import java.util.UUID;

public record Rotation(UUID userId, Issued next) {}
