package com.windrunner.server.llmproviders.claude.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeResponse(
        String id,
        String type,
        String role,
        String model,
        List<JsonNode> content,
        @JsonProperty("stop_reason")
        String stopReason,
        Usage usage,
        ErrorDetails error
) {
    public record Usage(
            @JsonProperty("input_tokens")
            Long inputTokens,
            @JsonProperty("output_tokens")
            Long outputTokens
    ) {
    }

    public record ErrorDetails(
            String type,
            String message
    ) {
    }
}
