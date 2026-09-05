package com.windrunner.server.audio.gemini.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "windrunner.audio.transcription.gemini")
public class GeminiTranscriptionProperties {

    private String apiKey;

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private String model = "gemini-2.5-flash";
}
