package com.olima.auth.token;

import java.time.Instant;

public record Issued(String token, Instant expiresAt) {}
