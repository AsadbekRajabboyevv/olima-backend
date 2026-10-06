package com.olima.common.util;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

public final class MdcContext {

  private MdcContext() {}

  public static Runnable wrap(Runnable task) {
    if (task == null) {
      return null;
    }
    Map<String, String> context = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      if (context != null) {
        MDC.setContextMap(context);
      } else {
        MDC.clear();
      }
      try {
        task.run();
      } finally {
        if (previous != null) {
          MDC.setContextMap(previous);
        } else {
          MDC.clear();
        }
      }
    };
  }

  public static <T> Callable<T> wrap(Callable<T> task) {
    if (task == null) {
      return null;
    }
    Map<String, String> context = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      if (context != null) {
        MDC.setContextMap(context);
      } else {
        MDC.clear();
      }
      try {
        return task.call();
      } finally {
        if (previous != null) {
          MDC.setContextMap(previous);
        } else {
          MDC.clear();
        }
      }
    };
  }
}
