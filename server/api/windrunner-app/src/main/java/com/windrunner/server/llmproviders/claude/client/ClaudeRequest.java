package com.windrunner.server.llmproviders.claude.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeRequest(
        String model,
        String system,
        List<JsonNode> messages,
        @JsonProperty("max_tokens")
        int maxTokens,
        Float temperature,
        List<ClaudeTool> tools
) {
    public record ClaudeTool(
            String name,
            String description,
            @JsonProperty("input_schema")
            JsonNode inputSchema
    ) {
    }
}
