package com.olima.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olima.security.config.SecurityProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  static SecurityProperties props(String key) {
    return new SecurityProperties(
        new SecurityProperties.Jwt("x".repeat(40), "olima", Duration.ofMinutes(15)),
        new SecurityProperties.Refresh(Duration.ofDays(1), "c", "/", true, "Strict"),
        new SecurityProperties.Login(5, Duration.ofMinutes(1)),
        new SecurityProperties.Password(8, true),
        new SecurityProperties.BootstrapAdmin("super", ""),
        new SecurityProperties.Cors(List.of()),
        Duration.ofSeconds(30),
        key);
  }

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void roundTrip() {
    SecretCipher cipher = new SecretCipher(props(KEY));
    String encrypted = cipher.encrypt("123456:ABC-secret-token");
    assertThat(encrypted).startsWith("enc:v1:").doesNotContain("secret");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("123456:ABC-secret-token");
  }

  @Test
  void sameInputEncryptsDifferentlyEachTime() {
    SecretCipher cipher = new SecretCipher(props(KEY));
    assertThat(cipher.encrypt("a")).isNotEqualTo(cipher.encrypt("a"));
  }

  @Test
  void legacyPlaintextIsReadAsIs() {
    SecretCipher cipher = new SecretCipher(props(KEY));
    assertThat(cipher.decrypt("123:plain")).isEqualTo("123:plain");
    assertThat(cipher.encrypt(null)).isNull();
  }

  @Test
  void wrongKeyFailsLoudly() {
    String encrypted = new SecretCipher(props(KEY)).encrypt("x");
    byte[] other = new byte[32];
    other[0] = 1;
    SecretCipher wrong = new SecretCipher(props(Base64.getEncoder().encodeToString(other)));
    assertThatThrownBy(() -> wrong.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keyMustBe32Bytes() {
    assertThatThrownBy(
            () -> new SecretCipher(props(Base64.getEncoder().encodeToString(new byte[16]))))
        .isInstanceOf(IllegalStateException.class);
  }
}
