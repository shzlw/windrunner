package com.windrunner.server.llmproviders.openai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAIResponseRequest(
        String model,
        Object input,
        String instructions,
        @JsonProperty("max_output_tokens")
        int maxOutputTokens,
        Reasoning reasoning,
        List<FunctionTool> tools,
        @JsonProperty("parallel_tool_calls")
        Boolean parallelToolCalls
) {

    public record Reasoning(String effort) {
    }

    public record FunctionTool(
            String type,
            String name,
            String description,
            boolean strict,
            JsonNode parameters
    ) {
    }
}
