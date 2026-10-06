package com.olima.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private final SecretCipher cipher;

  public EncryptedStringConverter(SecretCipher cipher) {
    this.cipher = cipher;
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return cipher.encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    return cipher.decrypt(dbData);
  }
}
