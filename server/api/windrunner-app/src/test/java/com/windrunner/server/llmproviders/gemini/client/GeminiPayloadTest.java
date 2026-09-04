package com.windrunner.server.llmproviders.gemini.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeminiPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsInteractionResponseWithFunctionCallStep() {
        GeminiResponse response = objectMapper.readValue("""
                {"id":"v1_int123","status":"requires_action","model":"gemini-3.1-flash-lite","steps":[{"signature":"sig-abc","type":"thought"},{"id":"call_123","type":"function_call","name":"propose_work_item_revision","arguments":{"proposedTitle":"Title"}},{"type":"model_output","content":[{"type":"text","text":"Done."}]}],"usage":{"total_input_tokens":12,"total_output_tokens":4,"total_tokens":16}}
                """, GeminiResponse.class);

        assertThat(response.id()).isEqualTo("v1_int123");
        assertThat(response.status()).isEqualTo("requires_action");
        assertThat(response.steps()).hasSize(3);

        // Thought step
        GeminiResponse.Step thoughtStep = response.steps().get(0);
        assertThat(thoughtStep.type()).isEqualTo("thought");
        assertThat(thoughtStep.signature()).isEqualTo("sig-abc");

        // Function call step
        GeminiResponse.Step fcStep = response.steps().get(1);
        assertThat(fcStep.type()).isEqualTo("function_call");
        assertThat(fcStep.id()).isEqualTo("call_123");
        assertThat(fcStep.name()).isEqualTo("propose_work_item_revision");
        assertThat(fcStep.arguments()).containsEntry("proposedTitle", "Title");

        // Model output step
        GeminiResponse.Step outputStep = response.steps().get(2);
        assertThat(outputStep.type()).isEqualTo("model_output");
        assertThat(outputStep.content()).hasSize(1);
        assertThat(outputStep.content().getFirst().text()).isEqualTo("Done.");

        // Usage
        assertThat(response.usage().inputTokens()).isEqualTo(12);
        assertThat(response.usage().outputTokens()).isEqualTo(4);
        assertThat(response.usage().totalTokens()).isEqualTo(16);
    }

    @Test
    void writesInteractionToolAndResultRequest() {
        GeminiRequest request = new GeminiRequest(
                "gemini-3.1-flash-lite",
                "Be helpful",
                List.of(
                        GeminiRequest.StepInput.functionResult("call_123", "propose_work_item_revision", "recorded"),
                        GeminiRequest.StepInput.functionResult("call_456", "fetch_work_item_details", "details")),
                List.of(GeminiRequest.Tool.function("propose_work_item_revision", "Propose revision", null)),
                new GeminiRequest.Config(2048, 1.0f),
                "v1_int123"
        );

        String payload = objectMapper.writeValueAsString(request);
        assertThat(payload).contains(
                "\"type\":\"function_result\"",
                "\"call_id\":\"call_123\"",
                "\"call_id\":\"call_456\"",
                "\"name\":\"propose_work_item_revision\"",
                "\"result\":[{\"type\":\"text\",\"text\":\"recorded\"}]");
        assertThat(payload).contains("\"type\":\"function\"", "\"name\":\"propose_work_item_revision\"");
        assertThat(payload).contains("\"generation_config\":{\"max_output_tokens\":2048,\"temperature\":1.0}");
        assertThat(payload).doesNotContain("\"config\":");
        assertThat(payload).contains("\"previous_interaction_id\":\"v1_int123\"");
        assertThat(payload).contains("\"model\":\"gemini-3.1-flash-lite\"");
    }
}
