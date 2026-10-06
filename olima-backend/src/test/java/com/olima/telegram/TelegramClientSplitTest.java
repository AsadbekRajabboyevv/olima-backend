package com.olima.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.olima.telegram.client.TelegramClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramClientSplitTest {

  @Test
  void shortTextIsOneMessage() {
    assertThat(TelegramClient.split("salom", 100)).containsExactly("salom");
  }

  @Test
  void longTextSplitsOnParagraphBoundary() {
    String first = "a".repeat(60);
    String second = "b".repeat(60);
    List<String> parts = TelegramClient.split(first + "\n\n" + second, 100);
    assertThat(parts).containsExactly(first, second);
  }

  @Test
  void noPartExceedsLimit() {
    String text = "so'z ".repeat(2000);
    List<String> parts = TelegramClient.split(text, 4000);
    assertThat(parts).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(4000));
    assertThat(String.join(" ", parts).replace(" ", "")).isEqualTo(text.replace(" ", ""));
  }
}
