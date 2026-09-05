package com.windrunner.server.llmproviders.openai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record OpenAIResponse(
        String id,
        String model,
        String status,
        @JsonProperty("incomplete_details")
        IncompleteDetails incompleteDetails,
        List<JsonNode> output,
        Usage usage
) {
    public record IncompleteDetails(String reason) {
    }

    public record Usage(
            @JsonProperty("input_tokens")
            Long inputTokens,
            @JsonProperty("output_tokens")
            Long outputTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }
}
