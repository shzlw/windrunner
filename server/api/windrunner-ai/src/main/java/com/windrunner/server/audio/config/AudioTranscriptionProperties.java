package com.windrunner.server.audio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

@Data
@ConfigurationProperties(prefix = "windrunner.audio.transcription")
public class AudioTranscriptionProperties {

    private boolean enabled;

    private String provider = "openai";

    private int maxDurationSeconds = 120;

    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);

    private OpenAIProperties openai = new OpenAIProperties();

    private GeminiProperties gemini = new GeminiProperties();

    public String configuredProvider() {
        if (provider == null || provider.isBlank()) {
            return "none";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    public String configuredModel() {
        return switch (configuredProvider()) {
            case "openai" -> openai.getModel();
            case "gemini" -> gemini.getModel();
            default -> null;
        };
    }

    @Data
    public static class OpenAIProperties {

        private String apiKey;

        private String baseUrl = "https://api.openai.com/v1";

        private String model = "gpt-transcribe";
    }

    @Data
    public static class GeminiProperties {

        private String apiKey;

        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

        private String model = "gemini-2.5-flash";
    }
}
