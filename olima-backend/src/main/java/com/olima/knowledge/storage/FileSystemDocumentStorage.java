package com.olima.knowledge.storage;

import com.olima.knowledge.config.KnowledgeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileSystemDocumentStorage implements DocumentStorage {

  private static final Pattern SAFE_KEY =
      Pattern.compile("^[0-9a-f]{2}/[0-9a-f-]{36}(\\.[a-z0-9]{1,10})?$");
  private static final Pattern SAFE_EXTENSION = Pattern.compile("^[a-z0-9]{1,10}$");

  private final Path root;

  public FileSystemDocumentStorage(KnowledgeProperties properties) {
    this.root = Path.of(properties.storage().path()).toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new UncheckedIOException("Document storage directory is not writable: " + root, e);
    }
    log.info("Document storage: {}", root);
  }

  @Override
  public String save(byte[] content, String extension) {
    String id = UUID.randomUUID().toString();
    String ext =
        extension != null && SAFE_EXTENSION.matcher(extension).matches() ? "." + extension : "";
    String key = id.substring(0, 2) + "/" + id + ext;
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      Files.write(tmp, content);
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
      return key;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store document", e);
    }
  }

  @Override
  public byte[] read(String key) {
    try {
      return Files.readAllBytes(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException("Stored document is missing: " + key, e);
    }
  }

  @Override
  public void delete(String key) {
    if (key == null) {
      return;
    }
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      log.warn("Failed to delete stored document {}: {}", key, e.getMessage());
    }
  }

  private Path resolve(String key) {
    if (!SAFE_KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    return path;
  }
}
