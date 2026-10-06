package com.olima.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Modul chegarasi: {@code com.olima.<modul>} boshqa modulning {@code *Repository} va
 * {@code *Entity} sinflarini import qilmaydi — faqat uning servisi va DTO'lari orqali ishlaydi.
 */
class ModuleBoundaryTest {

  private static final Path SOURCES = Path.of("src/main/java/com/olima");

  private static final Pattern FORBIDDEN_IMPORT =
      Pattern.compile("^import com\\.olima\\.([a-z]+)\\.([A-Za-z]+(?:Repository|Entity));$");

  /** Umumiy, hamma modul ishlata oladigan paket. */
  private static final Set<String> SHARED_MODULES = Set.of("common");

  /**
   * Hozircha ruxsat etilgan istisnolar ("modul -> modul.Sinf"). Ro'yxat faqat qisqarishi kerak;
   * istisno yo'qolsa, testni o'tkazish uchun bu yerdan ham olib tashlang.
   */
  private static final Set<String> KNOWN_EXCEPTIONS =
      Set.of(
          // Identifikatsiya infratuzilmasi: login, JWT, sessiya va widget kalitini tekshirish
          "auth -> organization.OrganizationEntity",
          "auth -> organization.OrganizationRepository",
          "auth -> user.UserEntity",
          "auth -> user.UserRepository",
          "security -> organization.OrganizationEntity",
          "security -> organization.OrganizationRepository",
          "security -> user.UserEntity",
          "security -> user.UserRepository",
          "config -> user.UserEntity",
          "config -> user.UserRepository",
          // Vosita bajaruvchilari ToolEntity bilan ishlaydi (ToolRegistry keshi entity saqlaydi)
          "agent -> tool.ToolEntity",
          "execution -> tool.ToolEntity",
          "execution -> tool.ToolParameterEntity",
          // Hisob-kitob hisobotlari llm_usage jadvalini to'g'ridan-to'g'ri o'qiydi
          "billing -> usage.LlmUsageEntity");

  @Test
  void modulesDoNotUseOtherModulesRepositoriesOrEntities() throws IOException {
    Set<String> found = crossModuleImports();

    assertThat(found)
        .as("Boshqa modulning Repository/Entity sinfi ishlatilgan — uning servisi yoki DTO'sidan"
            + " foydalaning")
        .isSubsetOf(KNOWN_EXCEPTIONS);
    assertThat(KNOWN_EXCEPTIONS)
        .as("Bu istisnolar endi yo'q — KNOWN_EXCEPTIONS dan olib tashlang")
        .isSubsetOf(found);
  }

  private static Set<String> crossModuleImports() throws IOException {
    Set<String> found = new TreeSet<>();
    try (Stream<Path> files = Files.walk(SOURCES)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String module = SOURCES.relativize(file).getName(0).toString();
        for (String line : Files.readAllLines(file)) {
          Matcher m = FORBIDDEN_IMPORT.matcher(line.strip());
          if (m.matches()
              && !m.group(1).equals(module)
              && !SHARED_MODULES.contains(m.group(1))) {
            found.add(module + " -> " + m.group(1) + "." + m.group(2));
          }
        }
      }
    }
    return found;
  }
}
