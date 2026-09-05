package com.windrunner.server.audio;

import com.windrunner.server.audio.client.GeminiTranscriptionRequest;
import com.windrunner.server.audio.client.GeminiTranscriptionResponse;
import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
public class GeminiTranscriptionProvider implements AudioTranscriptionProvider {

    private final RestClient restClient;
    private final AudioTranscriptionProperties.GeminiProperties properties;

    public GeminiTranscriptionProvider(
            RestClient restClient,
            AudioTranscriptionProperties.GeminiProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public String transcribe(AudioTranscriptionRequest request) {
        GeminiTranscriptionResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .build(properties.getModel()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(GeminiTranscriptionRequest.from(request))
                .retrieve()
                .body(GeminiTranscriptionResponse.class);

        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new AudioTranscriptionException("Gemini transcription returned no text");
        }

        log.info("Gemini transcription completed with model={}, audioBytes={}",
                properties.getModel(), request.audioBytes());
        return response.text().trim();
    }
}
