package com.olima.auth.dto;

import java.time.Instant;

public record Attempts(int failures, Instant lockedUntil) {}
