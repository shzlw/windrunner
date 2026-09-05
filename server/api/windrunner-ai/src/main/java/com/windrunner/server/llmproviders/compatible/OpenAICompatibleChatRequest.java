package com.windrunner.server.llmproviders.compatible;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAICompatibleChatRequest(
        String model,
        JsonNode messages,
        @JsonProperty("max_tokens")
        int maxTokens,
        @JsonProperty("reasoning_effort")
        String reasoningEffort,
        List<FunctionTool> tools,
        @JsonProperty("parallel_tool_calls")
        Boolean parallelToolCalls
) {

    public record FunctionTool(
            String type,
            FunctionDefinition function
    ) {
    }

    public record FunctionDefinition(
            String name,
            String description,
            JsonNode parameters
    ) {
    }
}
