package com.olima.security.model;

import java.time.Instant;

public record IssuedToken(String token, Instant expiresAt) {}
