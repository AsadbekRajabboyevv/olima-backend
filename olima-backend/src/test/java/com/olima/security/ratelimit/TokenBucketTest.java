package com.olima.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

  @Test
  void allowsBurstUpToCapacityThenRejects() {
    TokenBucket bucket = new TokenBucket(3, Duration.ofHours(1));
    assertThat(bucket.tryConsume()).isZero();
    assertThat(bucket.tryConsume()).isZero();
    assertThat(bucket.tryConsume()).isZero();
    long wait = bucket.tryConsume();
    assertThat(wait).isPositive();

    assertThat(Duration.ofNanos(wait)).isBetween(Duration.ofMinutes(19), Duration.ofMinutes(21));
  }

  @Test
  void refillsOverTime() throws InterruptedException {
    TokenBucket bucket = new TokenBucket(1, Duration.ofMillis(50));
    assertThat(bucket.tryConsume()).isZero();
    assertThat(bucket.tryConsume()).isPositive();
    Thread.sleep(80);
    assertThat(bucket.tryConsume()).isZero();
  }
}
