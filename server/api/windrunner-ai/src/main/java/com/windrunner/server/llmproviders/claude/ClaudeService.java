package com.windrunner.server.llmproviders.claude;

import com.windrunner.server.llm.*;
import com.windrunner.server.llmproviders.claude.client.ClaudeJsonSchema;
import com.windrunner.server.llmproviders.claude.client.ClaudeRequest;
import com.windrunner.server.llmproviders.claude.client.ClaudeResponse;
import com.windrunner.server.llmproviders.claude.config.ClaudeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ClaudeService implements LlmService {

    private final RestClient restClient;
    private final ClaudeProperties properties;
    private final ObjectMapper objectMapper;
    private final ClaudeJsonSchema jsonSchema;
    private final AgentService agentService;

    public ClaudeService(
            RestClient restClient,
            ClaudeProperties properties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonSchema = new ClaudeJsonSchema(objectMapper);
        this.agentService = agentService;
    }

    @Override
    public LlmResult<String> runChatWithTools(
            List<LlmMessage> messages,
            String instructions,
            List<LlmTool<?>> tools
    ) {
        return runToolLoop(createInitialMessages(messages), instructions, tools);
    }

    private LlmResult<String> runToolLoop(
            List<JsonNode> initialMessages,
            String instructions,
            List<LlmTool<?>> tools
    ) {
        List<LlmTool<?>> safeTools = tools == null ? List.of() : tools;
        List<ClaudeRequest.ClaudeTool> claudeTools = safeTools.stream()
                .map(this::toClaudeTool)
                .toList();

        List<JsonNode> conversationMessages = new ArrayList<>(initialMessages);
        UsageTotals usageTotals = new UsageTotals();
        AtomicBoolean executedTool = new AtomicBoolean();

        ClaudeResponse response = agentService.run(
                "Claude",
                safeTools,
                properties.getMaxToolRounds(),
                properties.getParallelToolTimeout(),
                new AgentService.AgentLoop<>() {
                    @Override
                    public ClaudeResponse callModel() {
                        ClaudeRequest request = buildRequest(conversationMessages, instructions, claudeTools);
                        ClaudeResponse response = callClaude(request);
                        usageTotals.add(response.usage());
                        return response;
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(ClaudeResponse response) {
                        return ClaudeService.this.findToolCalls(response);
                    }

                    @Override
                    public void preserveModelResponse(ClaudeResponse response) {
                        ClaudeService.this.preserveModelResponse(response, conversationMessages);
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                        executedTool.set(!results.isEmpty());
                        results.forEach(result -> ClaudeService.this.appendToolResult(
                                result.toolCall(), result.output(), conversationMessages));
                    }
                }
        );

        String output = requireOutputText(response, executedTool.get());
        logCompletion(response);
        return result(response, output, usageTotals);
    }

    private List<JsonNode> createInitialMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM chat message is required");
        }

        List<JsonNode> result = new ArrayList<>();
        for (LlmMessage message : messages) {
            if (message == null || !StringUtils.hasText(message.role()) || !StringUtils.hasText(message.content())) {
                throw new IllegalArgumentException("LLM chat messages require a role and content");
            }
            String role = ("assistant".equals(message.role()) || "model".equals(message.role())) ? "assistant" : "user";
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", role);
            node.put("content", message.content());
            result.add(node);
        }
        return result;
    }

    private ClaudeRequest buildRequest(
            List<JsonNode> messages,
            String instructions,
            List<ClaudeRequest.ClaudeTool> tools
    ) {
        boolean hasTools = !tools.isEmpty();
        return new ClaudeRequest(
                properties.getModel(),
                StringUtils.hasText(instructions) ? instructions : null,
                messages,
                properties.getMaxOutputTokens(),
                properties.getTemperature(),
                hasTools ? tools : null
        );
    }

    private ClaudeResponse callClaude(ClaudeRequest request) {
        try {
            ClaudeResponse response = restClient.post()
                    .uri("/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClaudeResponse.class);

            requireCompleteResponse(response);
            return response;
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (StringUtils.hasText(responseBody)) {
                throw new LlmException("Claude API call failed: " + responseBody, ex);
            }
            throw new LlmException("Claude API call failed with status " + ex.getStatusCode(), ex);
        }
    }

    private ClaudeRequest.ClaudeTool toClaudeTool(LlmTool<?> tool) {
        ObjectNode schema = jsonSchema.generate(tool.parametersType());
        return new ClaudeRequest.ClaudeTool(
                tool.name(),
                tool.description(),
                schema
        );
    }

    private List<LlmToolCall> findToolCalls(ClaudeResponse response) {
        if (response.content() == null || response.content().isEmpty()) {
            return List.of();
        }

        List<LlmToolCall> toolCalls = new ArrayList<>();
        for (JsonNode block : response.content()) {
            if ("tool_use".equals(block.path("type").asText())) {
                JsonNode inputNode = block.path("input");
                String argumentsJson = !inputNode.isMissingNode() && !inputNode.isNull()
                        ? inputNode.toString()
                        : "{}";
                toolCalls.add(new LlmToolCall(
                        block.path("id").asText(),
                        block.path("name").asText(),
                        argumentsJson
                ));
            }
        }
        return toolCalls;
    }

    private void preserveModelResponse(ClaudeResponse response, List<JsonNode> conversationMessages) {
        ObjectNode assistantMsg = objectMapper.createObjectNode();
        assistantMsg.put("role", "assistant");
        ArrayNode contentArray = assistantMsg.putArray("content");
        if (response.content() != null) {
            for (JsonNode block : response.content()) {
                contentArray.add(block.deepCopy());
            }
        }
        conversationMessages.add(assistantMsg);
    }

    private void appendToolResult(LlmToolCall toolCall, String output, List<JsonNode> conversationMessages) {
        ObjectNode toolResultBlock = objectMapper.createObjectNode();
        toolResultBlock.put("type", "tool_result");
        toolResultBlock.put("tool_use_id", toolCall.id());
        toolResultBlock.put("content", output);

        if (!conversationMessages.isEmpty()) {
            JsonNode lastMsgNode = conversationMessages.get(conversationMessages.size() - 1);
            if (lastMsgNode.isObject() && "user".equals(lastMsgNode.path("role").asText()) && lastMsgNode.path("content").isArray()) {
                ((ArrayNode) lastMsgNode.get("content")).add(toolResultBlock);
                return;
            }
        }

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode contentArray = userMsg.putArray("content");
        contentArray.add(toolResultBlock);
        conversationMessages.add(userMsg);
    }

    private String requireOutputText(ClaudeResponse response, boolean allowEmptyAfterToolCall) {
        if (response.content() != null) {
            for (JsonNode block : response.content()) {
                if ("text".equals(block.path("type").asText()) && StringUtils.hasText(block.path("text").asText())) {
                    return block.path("text").asText();
                }
            }
        }
        if (allowEmptyAfterToolCall) {
            return "";
        }
        throw new LlmException("Claude response did not contain text output");
    }

    private void requireCompleteResponse(ClaudeResponse response) {
        if (response == null) {
            throw new LlmException("Claude returned an empty response");
        }
        if (response.error() != null && StringUtils.hasText(response.error().message())) {
            throw new LlmException("Claude error: " + response.error().message());
        }
    }

    private <T> LlmResult<T> result(ClaudeResponse response, T output, UsageTotals usage) {
        return new LlmResult<>(
                response.id() != null ? response.id() : "claude-" + System.nanoTime(),
                response.model() != null ? response.model() : properties.getModel(),
                output,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens()
        );
    }

    private void logCompletion(ClaudeResponse response) {
        ClaudeResponse.Usage usage = response.usage();
        log.info("Claude Messages API completed with responseId={}, inputTokens={}, outputTokens={}",
                response.id(), usage != null ? usage.inputTokens() : null,
                usage != null ? usage.outputTokens() : null);
    }

    private static final class UsageTotals {

        private long inputTokens;
        private long outputTokens;
        private boolean present;

        private void add(ClaudeResponse.Usage usage) {
            if (usage == null) {
                return;
            }
            present = true;
            inputTokens += usage.inputTokens() != null ? usage.inputTokens() : 0;
            outputTokens += usage.outputTokens() != null ? usage.outputTokens() : 0;
        }

        private Long inputTokens() {
            return present ? inputTokens : null;
        }

        private Long outputTokens() {
            return present ? outputTokens : null;
        }

        private Long totalTokens() {
            return present ? (inputTokens + outputTokens) : null;
        }
    }
}
