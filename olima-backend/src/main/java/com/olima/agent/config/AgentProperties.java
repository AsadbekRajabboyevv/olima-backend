package com.olima.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.agent")
public record AgentProperties(
    @Min(1) @DefaultValue("20") int maxConcurrentRequests,
    @NotNull @DefaultValue("3s") Duration acquireTimeout,
    @NotNull @DefaultValue("3m") Duration streamTimeout,
    @DefaultValue("") String fallbackModel,
    @Valid @NotNull @DefaultValue History history,
    @Valid @NotNull @DefaultValue Messages messages) {

  public record History(
      @Min(0) @DefaultValue("15") int maxMessages,
      @Min(100) @DefaultValue("10000") int maxChars,
      @Min(100) @DefaultValue("1500") int maxSingleMessageChars) {}

  public record Messages(
      @NotBlank @DefaultValue("Suhbat: {org}") String conversationTitle,
      @NotBlank
          @DefaultValue(
              "Murojaatingiz tasdiqlandi va ro'yxatga olindi. Mas'ul xodim uni tashkilot belgilagan"
                  + " muddatda ko'rib chiqadi.")
          String complaintConfirmed,
      @NotBlank
          @DefaultValue("Javob tayyorlashda xatolik yuz berdi. Qayta urinib ko'ring (kod: {ref})")
          String streamError,
      @NotBlank @DefaultValue("[qisqartirildi]") String historyTruncated) {}

  public static String render(String template, String orgName) {
    return template.replace("{org}", orgName != null ? orgName : "");
  }
}
