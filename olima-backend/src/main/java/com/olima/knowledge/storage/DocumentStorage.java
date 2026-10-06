package com.olima.knowledge.storage;

public interface DocumentStorage {

  String save(byte[] content, String extension);

  byte[] read(String key);

  void delete(String key);
}
