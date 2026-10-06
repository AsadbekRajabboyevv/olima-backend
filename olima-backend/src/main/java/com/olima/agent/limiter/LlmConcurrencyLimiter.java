package com.olima.agent.limiter;

import com.olima.agent.config.AgentProperties;
import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class LlmConcurrencyLimiter {

  private final Semaphore permits;
  private final Duration acquireTimeout;
  private final int max;

  public LlmConcurrencyLimiter(AgentProperties properties, MeterRegistry meterRegistry) {
    this.max = properties.maxConcurrentRequests();
    this.permits = new Semaphore(max, true);
    this.acquireTimeout = properties.acquireTimeout();
    Gauge.builder("olima.llm.inflight", permits, p -> max - p.availablePermits())
        .register(meterRegistry);
  }

  public Permit acquire() {
    try {
      if (!permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new BusinessException(
            ErrorCode.SERVICE_BUSY,
            "Hozir so'rovlar ko'p. Bir necha soniyadan keyin qayta urinib ko'ring",
            null,
            Duration.ofSeconds(5));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.SERVICE_BUSY, "Request was interrupted");
    }
    return new Permit(permits);
  }

  public static final class Permit implements AutoCloseable {

    private final Semaphore semaphore;
    private boolean released;

    private Permit(Semaphore semaphore) {
      this.semaphore = semaphore;
    }

    @Override
    public synchronized void close() {
      if (!released) {
        released = true;
        semaphore.release();
      }
    }
  }
}
