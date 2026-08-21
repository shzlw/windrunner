package com.windrunner.server.llmproviders.gemini.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(
        String id,
        String status,
        String model,
        List<Step> steps,
        Usage usage,
        Error error
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String id,
            String type,
            // function_call fields
            @JsonProperty("call_id")
            String callId,
            String name,
            Map<String, Object> arguments,
            // model_output fields
            List<ContentPart> content,
            // thought fields
            String signature,
            List<ContentPart> summary
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentPart(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("total_input_tokens")
            Long totalInputTokens,
            @JsonProperty("prompt_tokens")
            Long promptTokens,
            @JsonProperty("total_output_tokens")
            Long totalOutputTokens,
            @JsonProperty("output_tokens")
            Long outputTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
        public Long inputTokens() {
            return totalInputTokens != null ? totalInputTokens : promptTokens;
        }

        public Long outputTokens() {
            return totalOutputTokens != null ? totalOutputTokens : outputTokens;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
            String message,
            Integer code
    ) {}
}
