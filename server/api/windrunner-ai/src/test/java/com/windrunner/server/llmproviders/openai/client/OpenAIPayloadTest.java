package com.windrunner.server.llmproviders.openai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

class OpenAIPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesPreviousResponseIdAndFunctionOutputs() {
        ArrayNode input = objectMapper.createArrayNode();
        input.addObject()
                .put("type", "function_call_output")
                .put("call_id", "call_123")
                .put("output", "details");
        OpenAIResponseRequest request = new OpenAIResponseRequest(
                "gpt-5.4",
                input,
                "Be helpful",
                2048,
                new OpenAIResponseRequest.Reasoning("medium"),
                List.of(),
                true,
                "resp_123"
        );

        String payload = objectMapper.writeValueAsString(request);

        assertThat(payload).contains(
                "\"previous_response_id\":\"resp_123\"",
                "\"type\":\"function_call_output\"",
                "\"call_id\":\"call_123\"",
                "\"output\":\"details\"",
                "\"instructions\":\"Be helpful\"");
    }
}
