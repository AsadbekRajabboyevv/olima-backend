package com.olima.knowledge.util;

import com.olima.knowledge.config.KnowledgeProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

  private final int chunkSize;
  private final int overlap;

  public TextChunker(KnowledgeProperties properties) {
    this.chunkSize = properties.chunking().size();

    this.overlap = Math.min(properties.chunking().overlap(), chunkSize / 2);
  }

  public List<String> chunk(String text) {
    return chunk(text, chunkSize, overlap);
  }

  public List<String> chunk(String text, int chunkSize, int overlap) {
    List<String> chunks = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return chunks;
    }

    String[] paragraphs = text.replace("\r\n", "\n").trim().split("\n\\s*\n");
    StringBuilder current = new StringBuilder();

    for (String rawParagraph : paragraphs) {
      String paragraph = rawParagraph.trim();
      if (paragraph.isEmpty()) {
        continue;
      }

      if (paragraph.length() > chunkSize) {
        if (!current.isEmpty()) {
          chunks.add(current.toString().trim());
          current.setLength(0);
        }
        chunks.addAll(splitOversizedParagraph(paragraph, chunkSize, overlap));
        continue;
      }

      if (!current.isEmpty() && current.length() + paragraph.length() + 2 > chunkSize) {
        chunks.add(current.toString().trim());
        current = new StringBuilder(overlapTail(current.toString(), overlap));
      }

      if (!current.isEmpty()) {
        current.append("\n\n");
      }
      current.append(paragraph);
    }

    if (!current.toString().isBlank()) {
      chunks.add(current.toString().trim());
    }
    return chunks;
  }

  private List<String> splitOversizedParagraph(String paragraph, int chunkSize, int overlap) {
    List<String> pieces = new ArrayList<>();
    int start = 0;
    while (start < paragraph.length()) {
      int end = Math.min(start + chunkSize, paragraph.length());
      pieces.add(paragraph.substring(start, end).trim());
      if (end == paragraph.length()) {
        break;
      }
      start = end - overlap;
    }
    return pieces;
  }

  private String overlapTail(String text, int overlap) {
    if (text.length() <= overlap) {
      return text;
    }
    return text.substring(text.length() - overlap);
  }
}
