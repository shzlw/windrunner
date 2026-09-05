package com.windrunner.server.audio;

import com.windrunner.server.audio.gemini.config.GeminiTranscriptionProperties;
import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import com.windrunner.server.audio.openai.config.OpenAITranscriptionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AudioTranscriptionAvailabilityService {

    private final AudioTranscriptionProperties properties;
    private final OpenAITranscriptionProperties openAIProperties;
    private final GeminiTranscriptionProperties geminiProperties;

    public String provider() {
        return properties.configuredProvider();
    }

    public String model() {
        return switch (provider()) {
            case "openai" -> openAIProperties.getModel();
            case "gemini" -> geminiProperties.getModel();
            default -> null;
        };
    }
}
