package com.windrunner.server.audio.gemini;

import com.windrunner.server.audio.AudioTranscriptionException;
import com.windrunner.server.audio.AudioTranscriptionProvider;
import com.windrunner.server.audio.AudioTranscriptionRequest;
import com.windrunner.server.audio.gemini.client.GeminiTranscriptionRequest;
import com.windrunner.server.audio.gemini.client.GeminiTranscriptionResponse;
import com.windrunner.server.audio.gemini.config.GeminiTranscriptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
public class GeminiTranscriptionProvider implements AudioTranscriptionProvider {

    private final RestClient restClient;
    private final GeminiTranscriptionProperties properties;

    public GeminiTranscriptionProvider(
            RestClient restClient,
            GeminiTranscriptionProperties properties
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
