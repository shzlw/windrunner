package com.windrunner.server.llmproviders.gemini.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequest(
        String model,
        @JsonProperty("system_instruction")
        String systemInstruction,
        Object input,
        List<Tool> tools,
        @JsonProperty("generation_config")
        Config generationConfig,
        @JsonProperty("previous_interaction_id")
        String previousInteractionId
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepInput(
            String type,
            @JsonProperty("call_id")
            String callId,
            String name,
            List<ContentPart> content,
            Map<String, Object> result
    ) {
        public static StepInput userInput(String text) {
            return new StepInput("user_input", null, null, List.of(new ContentPart("text", text)), null);
        }

        public static StepInput modelOutput(String text) {
            return new StepInput("model_output", null, null, List.of(new ContentPart("text", text)), null);
        }

        public static StepInput functionResult(String callId, String name, String output) {
            return new StepInput("function_result", callId, name, null, Map.of("result", output));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentPart(
            String type,
            String text
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            String type,
            String name,
            String description,
            JsonNode parameters
    ) {
        public static Tool function(String name, String description, JsonNode parameters) {
            return new Tool("function", name, description, parameters);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Config(
            @JsonProperty("max_output_tokens")
            Integer maxOutputTokens,
            Float temperature
    ) {}
}
