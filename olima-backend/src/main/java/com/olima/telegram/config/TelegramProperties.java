package com.olima.telegram.config;

import com.olima.telegram.TelegramUpdateMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.telegram")
public record TelegramProperties(
    @NotBlank @DefaultValue("https://api.telegram.org") String apiBaseUrl,
    @Min(100) @DefaultValue("4000") int maxMessageLength,
    @DefaultValue("Markdown") String parseMode,
    @DefaultValue("message") List<String> allowedUpdates,
    @NotNull @DefaultValue("3d") Duration processedUpdateRetention,
    // Yangi bot qaysi rejimda ulanadi (SUPER_ADMIN boshqasini tanlamasa)
    @NotNull @DefaultValue("WEBHOOK") TelegramUpdateMode defaultUpdateMode,
    // getUpdates "timeout" parametri: Telegram javobni shuncha ushlab turadi (maks. 50s)
    @NotNull @DefaultValue("30s") Duration pollingTimeout,
    // Xatodan keyin qayta urinishgacha kutish: 1s, 2s, 4s ... shu chegaragacha
    @NotNull @DefaultValue("30s") Duration pollingMaxBackoff,
    @NotBlank
        @DefaultValue(
            "Assalomu alaykum! Men {org} uchun AI yordamchiman. Savolingizni shu yerga yozing —"
                + " imkon qadar tez va aniq javob berishga harakat qilaman.")
        String startGreeting,
    @NotBlank
        @DefaultValue(
            "Kechirasiz, so'rovingizni qayta ishlashda xatolik yuz berdi. Birozdan so'ng qayta"
                + " urinib ko'ring.")
        String errorMessage,
    @NotBlank
        @DefaultValue(
            "Yangi murojaat ({org})\n\nMavzu: {subject}\nToifa: {category}\n\n{description}")
        String complaintNotification) {}
