package com.windrunner.server.audio.gemini.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiTranscriptionResponse(List<Candidate> candidates) {

    public String text() {
        if (candidates == null) {
            return null;
        }

        String text = candidates.stream()
                .filter(Objects::nonNull)
                .map(Candidate::content)
                .filter(Objects::nonNull)
                .map(Content::parts)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(Part::text)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
        return text.isBlank() ? null : text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text) {
    }
}
