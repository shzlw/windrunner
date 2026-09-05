package com.windrunner.server.audio.openai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAITranscriptionResponse(String text) {
}
