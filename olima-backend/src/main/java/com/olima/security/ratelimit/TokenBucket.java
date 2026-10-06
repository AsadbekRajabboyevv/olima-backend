package com.olima.security.ratelimit;

import java.time.Duration;

final class TokenBucket {

  private final int capacity;
  private final double refillPerNano;
  private double tokens;
  private long lastRefill;

  TokenBucket(int capacity, Duration period) {
    this.capacity = capacity;
    this.refillPerNano = (double) capacity / period.toNanos();
    this.tokens = capacity;
    this.lastRefill = System.nanoTime();
  }

  synchronized long tryConsume() {
    long now = System.nanoTime();
    tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
    lastRefill = now;
    if (tokens >= 1) {
      tokens -= 1;
      return 0;
    }
    return (long) Math.ceil((1 - tokens) / refillPerNano);
  }
}
