package com.olima.knowledge;

import java.util.List;

public interface EmbeddingService {

  boolean isEnabled();

  boolean isModelConfigured();

  boolean isVectorStoreAvailable();

  String modelName();

  float[] embed(String text);

  List<float[]> embedAll(List<String> texts);

  static String toPgVector(float[] vector) {
    StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vector[i]);
    }
    return sb.append(']').toString();
  }
}
