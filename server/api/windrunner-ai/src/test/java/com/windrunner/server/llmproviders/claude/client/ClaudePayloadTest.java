package com.windrunner.server.llmproviders.claude.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ClaudePayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsClaudeResponseWithToolUseAndThinkingBlocks() {
        ClaudeResponse response = objectMapper.readValue("""
                {
                  "id": "msg_013Z925153",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-3-7-sonnet-20250219",
                  "content": [
                    {
                      "type": "thinking",
                      "thinking": "Analyzing the request parameters...",
                      "signature": "sig_abc123"
                    },
                    {
                      "type": "text",
                      "text": "I will inspect the task."
                    },
                    {
                      "type": "tool_use",
                      "id": "toolu_01A09q906599",
                      "name": "propose_work_item_revision",
                      "input": {
                        "proposedTitle": "Title"
                      }
                    }
                  ],
                  "stop_reason": "tool_use",
                  "usage": {
                    "input_tokens": 25,
                    "output_tokens": 18
                  }
                }
                """, ClaudeResponse.class);

        assertThat(response.id()).isEqualTo("msg_013Z925153");
        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.model()).isEqualTo("claude-3-7-sonnet-20250219");
        assertThat(response.content()).hasSize(3);

        // Thinking block
        JsonNode thinkingBlock = response.content().get(0);
        assertThat(thinkingBlock.path("type").asText()).isEqualTo("thinking");
        assertThat(thinkingBlock.path("thinking").asText()).isEqualTo("Analyzing the request parameters...");
        assertThat(thinkingBlock.path("signature").asText()).isEqualTo("sig_abc123");

        // Text block
        JsonNode textBlock = response.content().get(1);
        assertThat(textBlock.path("type").asText()).isEqualTo("text");
        assertThat(textBlock.path("text").asText()).isEqualTo("I will inspect the task.");

        // Tool use block
        JsonNode toolUseBlock = response.content().get(2);
        assertThat(toolUseBlock.path("type").asText()).isEqualTo("tool_use");
        assertThat(toolUseBlock.path("id").asText()).isEqualTo("toolu_01A09q906599");
        assertThat(toolUseBlock.path("name").asText()).isEqualTo("propose_work_item_revision");
        assertThat(toolUseBlock.path("input").get("proposedTitle").asText()).isEqualTo("Title");

        // Usage
        assertThat(response.usage().inputTokens()).isEqualTo(25);
        assertThat(response.usage().outputTokens()).isEqualTo(18);
    }

    @Test
    void writesClaudeRequestWithToolResult() {
        ObjectNode userMsgNode = objectMapper.createObjectNode();
        userMsgNode.put("role", "user");
        userMsgNode.putArray("content")
                .addObject()
                .put("type", "tool_result")
                .put("tool_use_id", "toolu_01A09q906599")
                .put("content", "recorded");

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ClaudeRequest request = new ClaudeRequest(
                "claude-3-7-sonnet-20250219",
                "Be helpful",
                List.of(userMsgNode),
                2048,
                1.0f,
                List.of(new ClaudeRequest.ClaudeTool("propose_work_item_revision", "Propose revision", schema))
        );

        String payload = objectMapper.writeValueAsString(request);
        assertThat(payload).contains("\"model\":\"claude-3-7-sonnet-20250219\"");
        assertThat(payload).contains("\"system\":\"Be helpful\"");
        assertThat(payload).contains("\"max_tokens\":2048");
        assertThat(payload).contains("\"temperature\":1.0");
        assertThat(payload).contains("\"type\":\"tool_result\"");
        assertThat(payload).contains("\"tool_use_id\":\"toolu_01A09q906599\"");
        assertThat(payload).contains("\"input_schema\":{\"type\":\"object\"}");
    }
}
