package com.olima.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class OutboundUrlPolicyTest {

  private static OutboundUrlPolicy policy(
      boolean allowPrivate, List<String> allowed, List<String> blocked) {
    return new OutboundUrlPolicy(
        new HttpClientProperties(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            DataSize.ofKilobytes(10),
            3,
            "test",
            new Outbound(allowPrivate, allowed, blocked, List.of("http", "https"))));
  }

  private final OutboundUrlPolicy strict = policy(false, List.of(), List.of());

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://127.0.0.1/admin",
        "http://localhost:8080/actuator",
        "http://169.254.169.254/latest/meta-data/",
        "http://10.0.0.5/",
        "http://192.168.1.1/",
        "http://172.16.0.1/",
        "http://[::1]/",
        "http://[::ffff:127.0.0.1]/",
        "http://0.0.0.0/",
        "http://100.64.0.1/",
        "http://olima-postgres.internal/",
        "http://printer.local/"
      })
  void internalAddressesAreRejected(String url) {
    assertThatThrownBy(() -> strict.requireAllowed(url))
        .isInstanceOf(OutboundRequestException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///etc/passwd",
        "ftp://example.com/",
        "gopher://x/",
        "http://user:pass@example.com/"
      })
  void nonHttpSchemesAndCredentialsAreRejected(String url) {
    assertThatThrownBy(() -> strict.requireAllowed(url))
        .isInstanceOf(OutboundRequestException.class);
  }

  @Test
  void allowListOverridesPrivateCheck() {
    OutboundUrlPolicy policy = policy(false, List.of("localhost"), List.of());
    assertThat(policy.isAllowed("http://localhost:9000/api")).isTrue();
  }

  @Test
  void wildcardBlockListWins() {
    OutboundUrlPolicy policy = policy(true, List.of(), List.of("*.evil.test"));
    assertThat(policy.isAllowed("http://api.evil.test/")).isFalse();
    assertThat(policy.isAllowed("http://localhost/")).isTrue();
  }

  @Test
  void wildcardDoesNotMatchBareDomain() {
    assertThat(OutboundUrlPolicy.matchesAny("example.uz", List.of("*.example.uz"))).isFalse();
    assertThat(OutboundUrlPolicy.matchesAny("a.example.uz", List.of("*.example.uz"))).isTrue();
  }

  @Test
  void publicAddressIsNotInternal() throws Exception {
    assertThat(OutboundUrlPolicy.isInternalAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    assertThat(OutboundUrlPolicy.isInternalAddress(InetAddress.getByName("198.18.0.1"))).isTrue();
  }
}
