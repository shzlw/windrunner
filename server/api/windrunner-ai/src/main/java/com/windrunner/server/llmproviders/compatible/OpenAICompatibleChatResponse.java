package com.windrunner.server.llmproviders.compatible;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAICompatibleChatResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {

    public record Choice(
            Message message,
            @JsonProperty("finish_reason")
            String finishReason
    ) {
    }

    public record Message(
            String role,
            String content,
            @JsonProperty("tool_calls")
            List<ToolCall> toolCalls
    ) {
    }

    public record ToolCall(
            String id,
            String type,
            FunctionCall function
    ) {
    }

    public record FunctionCall(
            String name,
            String arguments
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens")
            Long promptTokens,
            @JsonProperty("completion_tokens")
            Long completionTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }
}
