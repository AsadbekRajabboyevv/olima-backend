package com.olima.knowledge;

import com.olima.knowledge.dto.Fetched;

public interface DocumentParserService {

  Fetched fetch(String url);

  String extractText(byte[] content, String resourceName, String contentType);
}
