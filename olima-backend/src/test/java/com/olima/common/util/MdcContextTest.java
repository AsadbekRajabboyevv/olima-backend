package com.olima.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcContextTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void wrapRunnable_propagatesContextToOtherThreadAndCleansUp() throws Exception {
    MDC.put("requestId", "req-123");
    MDC.put("orgId", "org-456");

    AtomicReference<String> workerReqId = new AtomicReference<>();
    AtomicReference<String> workerOrgId = new AtomicReference<>();
    AtomicReference<String> afterTaskReqId = new AtomicReference<>();

    Runnable task =
        () -> {
          workerReqId.set(MDC.get("requestId"));
          workerOrgId.set(MDC.get("orgId"));
        };

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> future = executor.submit(MdcContext.wrap(task));
      future.get();

      Future<?> checkCleanup =
          executor.submit(
              () -> {
                afterTaskReqId.set(MDC.get("requestId"));
              });
      checkCleanup.get();
    }

    assertThat(workerReqId.get()).isEqualTo("req-123");
    assertThat(workerOrgId.get()).isEqualTo("org-456");
    assertThat(afterTaskReqId.get()).isNull();
    assertThat(MDC.get("requestId")).isEqualTo("req-123");
  }

  @Test
  void wrapCallable_propagatesContextAndReturnsValue() throws Exception {
    MDC.put("requestId", "req-abc");

    Callable<String> task = () -> MDC.get("requestId") + "-done";

    String result;
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<String> future = executor.submit(MdcContext.wrap(task));
      result = future.get();
    }

    assertThat(result).isEqualTo("req-abc-done");
  }

  @Test
  void wrap_handlesNullCallerContextGracefully() throws Exception {
    MDC.clear();

    AtomicReference<String> workerReqId = new AtomicReference<>();
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      // First populate worker thread with dirty MDC
      executor
          .submit(
              () -> {
                MDC.put("dirtyKey", "dirtyVal");
              })
          .get();

      // Submit wrapped task with empty caller context
      Future<?> future =
          executor.submit(
              MdcContext.wrap(
                  () -> {
                    workerReqId.set(MDC.get("dirtyKey"));
                  }));
      future.get();
    }

    assertThat(workerReqId.get()).isNull();
  }

  @Test
  void wrap_cleansUpEvenWhenTaskThrowsException() throws Exception {
    MDC.put("requestId", "req-err");

    AtomicReference<String> afterErrorReqId = new AtomicReference<>();
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> future =
          executor.submit(
              MdcContext.wrap(
                  (Runnable)
                      () -> {
                        throw new RuntimeException("Task failed");
                      }));
      assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class);

      executor
          .submit(
              () -> {
                afterErrorReqId.set(MDC.get("requestId"));
              })
          .get();
    }

    assertThat(afterErrorReqId.get()).isNull();
  }
}
