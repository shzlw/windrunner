package com.windrunner.server.audio.openai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "windrunner.audio.transcription.openai")
public class OpenAITranscriptionProperties {

    private String apiKey;

    private String baseUrl = "https://api.openai.com/v1";

    private String model = "gpt-transcribe";
}
