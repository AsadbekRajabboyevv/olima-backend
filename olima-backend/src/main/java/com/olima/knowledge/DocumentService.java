package com.olima.knowledge;

import com.olima.knowledge.dto.DocumentResponse;
import com.olima.knowledge.dto.TextDocumentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

  List<DocumentResponse> findDocuments(UUID knowledgeBaseId);

  DocumentResponse uploadDocument(UUID knowledgeBaseId, MultipartFile file, String title);

  DocumentResponse uploadDocumentFromUrl(UUID knowledgeBaseId, String url, String title);

  DocumentResponse createTextDocument(UUID knowledgeBaseId, TextDocumentRequest request);

  void deleteDocument(UUID documentId);

  DocumentResponse reindexDocument(UUID documentId);
}
