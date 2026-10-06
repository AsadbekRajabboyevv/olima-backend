package com.olima.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olima.common.http.OutboundUrlPolicy;
import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.DocumentResponse;
import com.olima.knowledge.dto.TextDocumentRequest;
import com.olima.knowledge.enums.DocumentProcessingStatus;
import com.olima.knowledge.ingestion.DocumentQueuedEvent;
import com.olima.knowledge.storage.DocumentStorage;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class DocumentServiceTest {

  private DocumentRepository documentRepository;
  private DocumentChunkRepository documentChunkRepository;
  private EmbeddingService embeddingService;
  private DocumentStorage storage;
  private OutboundUrlPolicy urlPolicy;
  private ApplicationEventPublisher events;
  private KnowledgeProperties properties;
  private KnowledgeLookup lookup;
  private DocumentServiceImpl service;

  @BeforeEach
  void setUp() {
    documentRepository = mock(DocumentRepository.class);
    documentChunkRepository = mock(DocumentChunkRepository.class);
    embeddingService = mock(EmbeddingService.class);
    storage = mock(DocumentStorage.class);
    urlPolicy = mock(OutboundUrlPolicy.class);
    events = mock(ApplicationEventPublisher.class);
    lookup = mock(KnowledgeLookup.class);

    properties =
        new KnowledgeProperties(
            new KnowledgeProperties.Ingestion(
                DataSize.ofMegabytes(25),
                5000,
                Duration.ofMinutes(2),
                List.of("pdf", "txt", "docx"),
                true,
                null),
            null,
            null,
            null,
            null);

    service =
        new DocumentServiceImpl(
            documentRepository,
            documentChunkRepository,
            embeddingService,
            storage,
            urlPolicy,
            events,
            properties,
            lookup);
  }

  @Test
  void resolveTitle_resolvesVariousPatterns() {
    KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().name("Toshkent Davlat Universiteti").build();

    // 1. Explicit title wins
    assertThat(service.resolveTitle("Custom Title", "foo.pdf", kb)).isEqualTo("Custom Title");

    // 2. Date prefix stripped, hyphens replaced, capitalized
    assertThat(service.resolveTitle(null, "20240901_internal-regulations.pdf", kb))
        .isEqualTo("Internal regulations");

    // 3. Generic name falls back to KB name + " hujjati"
    assertThat(service.resolveTitle(null, "doc.pdf", kb))
        .isEqualTo("Toshkent Davlat Universiteti hujjati");

    // 4. Generic name with empty KB name falls back to "Rasmiy hujjat"
    KnowledgeBaseEntity emptyKb = KnowledgeBaseEntity.builder().name("").build();
    assertThat(service.resolveTitle(null, "file.docx", emptyKb)).isEqualTo("Rasmiy hujjat");
  }

  @Test
  void extension_extractsExtensionInLowerCase() {
    assertThat(DocumentServiceImpl.extension("sample.PDF")).isEqualTo("pdf");
    assertThat(DocumentServiceImpl.extension("archive.tar.gz")).isEqualTo("gz");
    assertThat(DocumentServiceImpl.extension("no_extension")).isNull();
    assertThat(DocumentServiceImpl.extension(".hidden")).isNull();
  }

  @Test
  void uploadDocument_emptyFile_throwsIllegalArgumentException() {
    UUID kbId = UUID.randomUUID();
    when(lookup.requireKnowledgeBase(kbId)).thenReturn(KnowledgeBaseEntity.builder().build());

    MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

    assertThatThrownBy(() -> service.uploadDocument(kbId, emptyFile, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void uploadDocument_unsupportedExtension_throwsIllegalArgumentException() {
    UUID kbId = UUID.randomUUID();
    when(lookup.requireKnowledgeBase(kbId)).thenReturn(KnowledgeBaseEntity.builder().build());

    MockMultipartFile file =
        new MockMultipartFile("file", "script.exe", "application/octet-stream", "dummy content".getBytes());

    assertThatThrownBy(() -> service.uploadDocument(kbId, file, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("File type is not supported");
  }

  @Test
  void uploadDocument_validFile_storesContentSavesDocumentAndPublishesEvent() {
    UUID kbId = UUID.randomUUID();
    KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().name("Qoidalar").build();
    kb.setId(kbId);
    when(lookup.requireKnowledgeBase(kbId)).thenReturn(kb);

    byte[] content = "Hello PDF content".getBytes();
    MockMultipartFile file =
        new MockMultipartFile("file", "guidelines.pdf", "application/pdf", content);

    when(storage.save(content, "pdf")).thenReturn("key-guidelines-123");

    UUID generatedId = UUID.randomUUID();
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(inv -> {
          DocumentEntity entity = inv.getArgument(0);
          entity.setId(generatedId);
          return entity;
        });

    DocumentResponse response = service.uploadDocument(kbId, file, "Maxsus Qoidalar");

    assertThat(response.id()).isEqualTo(generatedId);
    assertThat(response.title()).isEqualTo("Maxsus Qoidalar");
    assertThat(response.status()).isEqualTo(DocumentProcessingStatus.PENDING);

    ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
    verify(documentRepository).save(captor.capture());
    DocumentEntity saved = captor.getValue();
    assertThat(saved.getKnowledgeBaseId()).isEqualTo(kbId);
    assertThat(saved.getStorageKey()).isEqualTo("key-guidelines-123");
    assertThat(saved.getStatus()).isEqualTo(DocumentProcessingStatus.PENDING);
    assertThat(saved.getContentHash()).isNotBlank();

    ArgumentCaptor<DocumentQueuedEvent> eventCaptor =
        ArgumentCaptor.forClass(DocumentQueuedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().documentId()).isEqualTo(generatedId);
  }

  @Test
  void createTextDocument_tooLong_throwsIllegalArgumentException() {
    UUID kbId = UUID.randomUUID();
    when(lookup.requireKnowledgeBase(kbId)).thenReturn(KnowledgeBaseEntity.builder().build());

    String longContent = "a".repeat(5001);
    TextDocumentRequest request = new TextDocumentRequest("Title", longContent);

    assertThatThrownBy(() -> service.createTextDocument(kbId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Text is too long");
  }

  @Test
  void uploadDocumentFromUrl_validUrl_savesDocumentAndPublishesEvent() {
    UUID kbId = UUID.randomUUID();
    when(lookup.requireKnowledgeBase(kbId)).thenReturn(KnowledgeBaseEntity.builder().build());
    when(urlPolicy.requireAllowed("https://example.com/doc"))
        .thenReturn(URI.create("https://example.com/doc"));

    UUID docId = UUID.randomUUID();
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(inv -> {
          DocumentEntity entity = inv.getArgument(0);
          entity.setId(docId);
          return entity;
        });

    DocumentResponse response =
        service.uploadDocumentFromUrl(kbId, "https://example.com/doc", "Web Hujjat");

    assertThat(response.id()).isEqualTo(docId);
    assertThat(response.title()).isEqualTo("Web Hujjat");
    assertThat(response.status()).isEqualTo(DocumentProcessingStatus.PENDING);

    verify(events).publishEvent(any(DocumentQueuedEvent.class));
  }

  @Test
  void deleteDocument_whenNotFound_noop() {
    UUID docId = UUID.randomUUID();
    when(documentRepository.findById(docId)).thenReturn(Optional.empty());

    service.deleteDocument(docId);

    verifyNoInteractions(storage);
  }

  @Test
  void deleteDocument_whenFound_deletesFromRepositoryAndStorage() {
    UUID docId = UUID.randomUUID();
    DocumentEntity doc =
        DocumentEntity.builder().storageKey("storage-key-xyz").build();
    doc.setId(docId);
    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

    service.deleteDocument(docId);

    verify(documentRepository).delete(doc);
    verify(storage).delete("storage-key-xyz");
  }
}
