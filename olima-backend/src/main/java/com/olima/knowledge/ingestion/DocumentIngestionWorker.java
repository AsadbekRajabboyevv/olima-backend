package com.olima.knowledge.ingestion;

import com.olima.config.AgentExecutorConfig;
import com.olima.knowledge.config.KnowledgeProperties;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class DocumentIngestionWorker {

  private final DocumentQueue queue;
  private final DocumentIngestionProcessor processor;
  private final ExecutorService executor;
  private final KnowledgeProperties.Worker config;
  private final Semaphore permits;

  public DocumentIngestionWorker(
      DocumentQueue queue,
      DocumentIngestionProcessor processor,
      @Qualifier(AgentExecutorConfig.AGENT_EXECUTOR) ExecutorService executor,
      KnowledgeProperties properties) {
    this.queue = queue;
    this.processor = processor;
    this.executor = executor;
    this.config = properties.ingestion().worker();
    this.permits = new Semaphore(config.concurrency());
  }

  @Scheduled(
      fixedDelayString = "${app.knowledge.ingestion.worker.poll-interval:5s}",
      initialDelayString = "${app.knowledge.ingestion.worker.initial-delay:10s}")
  public void poll() {
    if (!config.enabled()) {
      return;
    }
    try {
      int requeued = queue.requeueStale(config.staleAfter());
      if (requeued > 0) {
        log.warn("Re-queued {} document(s) stuck in PROCESSING", requeued);
      }
      drain();
    } catch (Exception e) {
      log.error("Document ingestion poll failed: {}", e.getMessage(), e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onQueued(DocumentQueuedEvent event) {
    if (config.enabled()) {
      executor.execute(this::drain);
    }
  }

  void drain() {
    while (permits.tryAcquire()) {
      List<UUID> claimed;
      try {
        claimed = queue.claim(1);
      } catch (Exception e) {
        permits.release();
        throw e;
      }
      if (claimed.isEmpty()) {
        permits.release();
        return;
      }
      UUID documentId = claimed.getFirst();
      executor.execute(
          () -> {
            try {
              processor.process(documentId);
            } catch (Exception e) {
              log.error("Unexpected error while ingesting document {}", documentId, e);
            } finally {
              permits.release();
            }
            drain();
          });
    }
  }
}
