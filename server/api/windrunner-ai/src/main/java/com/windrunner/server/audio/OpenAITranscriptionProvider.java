package com.windrunner.server.audio;

import com.windrunner.server.audio.client.OpenAITranscriptionResponse;
import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
public class OpenAITranscriptionProvider implements AudioTranscriptionProvider {

    private final RestClient restClient;
    private final AudioTranscriptionProperties.OpenAIProperties properties;

    public OpenAITranscriptionProvider(
            RestClient restClient,
            AudioTranscriptionProperties.OpenAIProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public String transcribe(AudioTranscriptionRequest request) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", request.audio())
                .filename(request.filename())
                .contentType(request.mediaType());
        bodyBuilder.part("model", properties.getModel());
        bodyBuilder.part("response_format", "json");
        if (StringUtils.hasText(request.language())) {
            bodyBuilder.part("language", request.language());
        }

        OpenAITranscriptionResponse response = restClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .retrieve()
                .body(OpenAITranscriptionResponse.class);

        if (response == null || !StringUtils.hasText(response.text())) {
            throw new AudioTranscriptionException("OpenAI transcription returned no text");
        }

        log.info("OpenAI transcription completed with model={}, audioBytes={}",
                properties.getModel(), request.audioBytes());
        return response.text().trim();
    }
}
