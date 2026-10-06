package com.olima.integration.token;

import java.time.Duration;

public record CachedToken(String value, Duration ttl) {}
