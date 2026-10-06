package com.olima.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.olima.common.util.SlugGenerator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

  @Test
  void uzbekApostrophesAreRemoved() {
    assertThat(SlugGenerator.slugify("O'zbekiston Oliy ta'lim")).isEqualTo("ozbekiston-oliy-talim");
    assertThat(SlugGenerator.slugify("Oʻzbekiston")).isEqualTo("ozbekiston");
  }

  @Test
  void cyrillicIsTransliterated() {
    assertThat(SlugGenerator.slugify("Тошкент университети")).isEqualTo("toshkent-universiteti");
    assertThat(SlugGenerator.slugify("Ўқув маркази")).isEqualTo("oquv-markazi");
  }

  @Test
  void symbolsCollapseToSingleDash() {
    assertThat(SlugGenerator.slugify("  IT -- Park!!  ")).isEqualTo("it-park");
    assertThat(SlugGenerator.slugify("???")).isEqualTo("org");
  }

  @Test
  void uniqueAddsSuffix() {
    Set<String> taken = Set.of("tatu", "tatu-2");
    assertThat(SlugGenerator.unique("TATU", taken::contains)).isEqualTo("tatu-3");
  }
}
