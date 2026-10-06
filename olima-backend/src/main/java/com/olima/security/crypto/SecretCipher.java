package com.olima.security.crypto;

import com.olima.security.config.SecurityProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

  private static final String PREFIX = "enc:v1:";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKey key;

  public SecretCipher(SecurityProperties properties) {
    String configured = properties.encryptionKey() == null ? "" : properties.encryptionKey().trim();
    if (configured.isEmpty() || configured.startsWith("${")) {
      throw new IllegalStateException(
          "ENCRYPTION_KEY is not set. Generate one with 'openssl rand -base64 32' or run with"
              + " SPRING_PROFILES_ACTIVE=local for development");
    }
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(configured);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("app.security.encryption-key must be Base64", e);
    }
    if (raw.length != 32) {
      throw new IllegalStateException(
          "app.security.encryption-key must be 32 bytes (Base64), got " + raw.length);
    }
    this.key = new SecretKeySpec(raw, "AES");
  }

  public static boolean isEncrypted(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  public String encrypt(String plain) {
    if (plain == null || isEncrypted(plain)) {
      return plain;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
      return PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  public String decrypt(String stored) {
    if (stored == null || !isEncrypted(stored)) {
      return stored;
    }
    try {
      byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Decryption failed — wrong app.security.encryption-key?", e);
    }
  }
}
