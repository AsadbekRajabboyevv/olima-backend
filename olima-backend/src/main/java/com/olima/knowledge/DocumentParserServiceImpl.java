package com.olima.knowledge;

import com.olima.common.http.Request;
import com.olima.common.http.Response;
import com.olima.common.http.SafeHttpClient;
import com.olima.common.util.MdcContext;
import com.olima.config.AgentExecutorConfig;
import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.Fetched;
import com.olima.knowledge.exception.DocumentParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Slf4j
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

  private static final String ACCEPT_DOCUMENTS =
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

  private final KnowledgeProperties.Ingestion ingestion;
  private final SafeHttpClient httpClient;
  private final ExecutorService executor;

  public DocumentParserServiceImpl(
      KnowledgeProperties properties,
      SafeHttpClient httpClient,
      @Qualifier(AgentExecutorConfig.AGENT_EXECUTOR) ExecutorService executor) {
    this.ingestion = properties.ingestion();
    this.httpClient = httpClient;
    this.executor = executor;
  }

  @Override
  public Fetched fetch(String url) {
    Request request =
        Request.of(HttpMethod.GET, URI.create(url.trim()))
            .withHeaders(Map.of("Accept", ACCEPT_DOCUMENTS))
            .withMaxSize(ingestion.maxDownloadSize());
    Response response = httpClient.execute(request);
    if (!response.isSuccess()) {
      DocumentParseException ex =
          new DocumentParseException("Web page returned HTTP " + response.status() + ": " + url);
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    }
    if (response.truncated()) {
      log.warn("Download from {} exceeded {} and was truncated", url, ingestion.maxDownloadSize());
    }
    Fetched result =
        new Fetched(response.body(), response.contentType(), response.finalUri().toString());
    return result;
  }

  @Override
  public String extractText(byte[] content, String resourceName, String contentType) {
    Metadata metadata = new Metadata();
    if (resourceName != null) {
      metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceName);
    }
    if (contentType != null) {
      metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
    }
    Future<String> task =
        executor.submit(MdcContext.wrap(() -> parse(content, metadata, resourceName)));
    try {
      String text = task.get(ingestion.parseTimeout().toMillis(), TimeUnit.MILLISECONDS);
      return text;
    } catch (TimeoutException e) {
      task.cancel(true);
      DocumentParseException ex =
          new DocumentParseException(
              "Parsing timed out after " + ingestion.parseTimeout() + ": " + resourceName);
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    } catch (InterruptedException e) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      DocumentParseException ex =
          new DocumentParseException("Parsing was interrupted: " + resourceName, e);
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    } catch (ExecutionException e) {
      if (e.getCause() instanceof DocumentParseException dpe) {
        log.error("Error {} {}", dpe.getMessage(), ExceptionUtils.getStackTrace(dpe));
        throw dpe;
      }
      DocumentParseException ex =
          new DocumentParseException("Could not parse: " + resourceName, e.getCause());
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    }
  }

  private String parse(byte[] content, Metadata metadata, String source) {
    BodyContentHandler handler = new BodyContentHandler(ingestion.maxExtractedChars());
    try {
      new AutoDetectParser()
          .parse(new ByteArrayInputStream(content), handler, metadata, new ParseContext());
    } catch (SAXException e) {
      if (!WriteLimitReachedException.isWriteLimitReached(e)) {
        log.warn("Failed to parse {}: {}", source, e.getMessage());
        DocumentParseException ex = new DocumentParseException("Could not parse: " + source, e);
        log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
        throw ex;
      }
      log.warn("Extracted text of {} truncated at {} chars", source, ingestion.maxExtractedChars());
    } catch (IOException | TikaException e) {
      log.warn("Failed to parse {}: {}", source, e.getMessage());
      DocumentParseException ex = new DocumentParseException("Could not parse: " + source, e);
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    }
    String text = handler.toString().trim();
    if (text.isEmpty()) {
      DocumentParseException ex =
          new DocumentParseException("No extractable text found in: " + source);
      log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
      throw ex;
    }
    return text;
  }
}
