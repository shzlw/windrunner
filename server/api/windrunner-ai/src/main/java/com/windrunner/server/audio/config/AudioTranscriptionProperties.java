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

    public String configuredProvider() {
        if (provider == null || provider.isBlank()) {
            return "none";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

}
