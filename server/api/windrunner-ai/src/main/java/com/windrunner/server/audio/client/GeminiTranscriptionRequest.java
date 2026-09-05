package com.windrunner.server.audio.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.windrunner.server.audio.AudioTranscriptionException;
import com.windrunner.server.audio.AudioTranscriptionRequest;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiTranscriptionRequest(List<Content> contents) {

    private static final String TRANSCRIPTION_PROMPT =
            "Transcribe the spoken words in this audio. Return only the transcription without commentary.";

    public static GeminiTranscriptionRequest from(AudioTranscriptionRequest request) {
        try (var inputStream = request.audio().getInputStream()) {
            String encodedAudio = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
            return new GeminiTranscriptionRequest(List.of(new Content(List.of(
                    Part.text(TRANSCRIPTION_PROMPT),
                    Part.inlineData(request.mediaType().getType() + "/" + request.mediaType().getSubtype(), encodedAudio)
            ))));
        } catch (IOException exception) {
            throw new AudioTranscriptionException("Could not read audio for Gemini transcription", exception);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(List<Part> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(
            String text,
            @JsonProperty("inline_data")
            InlineData inlineData
    ) {
        public static Part text(String value) {
            return new Part(value, null);
        }

        public static Part inlineData(String mimeType, String data) {
            return new Part(null, new InlineData(mimeType, data));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InlineData(
            @JsonProperty("mime_type")
            String mimeType,
            String data
    ) {
    }
}
