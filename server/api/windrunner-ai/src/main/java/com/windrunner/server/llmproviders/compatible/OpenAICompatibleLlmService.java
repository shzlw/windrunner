package com.windrunner.server.llmproviders.compatible;

import com.windrunner.server.llm.*;
import com.windrunner.server.llmproviders.openai.client.OpenAIJsonSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared implementation for providers that expose an OpenAI-compatible LLM API.
 */
@Slf4j
public class OpenAICompatibleLlmService implements LlmService {

    private final RestClient restClient;
    private final OpenAICompatibleSettings settings;
    private final ObjectMapper objectMapper;
    private final OpenAIJsonSchema jsonSchema;
    private final AgentService agentService;

    public OpenAICompatibleLlmService(
            RestClient restClient,
            OpenAICompatibleSettings settings,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.restClient = restClient;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.jsonSchema = new OpenAIJsonSchema(objectMapper);
        this.agentService = agentService;
    }

    @Override
    public LlmResult<String> runChatWithTools(
            List<LlmMessage> messages,
            String instructions,
            List<LlmTool<?>> functions
    ) {
        return runToolLoop(createChatInput(messages), instructions, functions);
    }

    private LlmResult<String> runToolLoop(
            ArrayNode input,
            String instructions,
            List<LlmTool<?>> tools
    ) {
        List<LlmTool<?>> safeTools = tools == null ? List.of() : tools;
        List<OpenAICompatibleChatRequest.FunctionTool> chatTools = safeTools.stream()
                .map(this::toFunctionTool)
                .toList();

        ArrayNode conversation = input;
        UsageTotals usageTotals = new UsageTotals();
        AtomicBoolean executedTool = new AtomicBoolean();

        OpenAICompatibleChatResponse response = agentService.run(
                settings.providerName(),
                safeTools,
                settings.maxToolRounds(),
                settings.parallelToolTimeout(),
                new AgentService.AgentLoop<>() {
                    @Override
                    public OpenAICompatibleChatResponse callModel() {
                        OpenAICompatibleChatResponse response = callChatCompletions(
                                buildRequest(conversation, chatTools, instructions));
                        usageTotals.add(response.usage());
                        return response;
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(OpenAICompatibleChatResponse response) {
                        return OpenAICompatibleLlmService.this.findToolCalls(response);
                    }

                    @Override
                    public void preserveModelResponse(OpenAICompatibleChatResponse response) {
                        appendAssistantMessage(conversation, response);
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                        executedTool.set(!results.isEmpty());
                        results.forEach(result -> appendToolResult(
                                conversation, result.toolCall(), result.output()));
                    }
                }
        );

        String output = requireOutputText(response, executedTool.get());
        logCompletion(response);
        return result(response, output, usageTotals);
    }

    private void appendAssistantMessage(
            ArrayNode conversation,
            OpenAICompatibleChatResponse response
    ) {
        OpenAICompatibleChatResponse.Message message = firstChoice(response).message();
        var assistantMessage = conversation.addObject()
                .put("role", "assistant");
        if (message.content() != null) {
            assistantMessage.put("content", message.content());
        } else {
            assistantMessage.putNull("content");
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            var toolCalls = assistantMessage.putArray("tool_calls");
            for (OpenAICompatibleChatResponse.ToolCall toolCall : message.toolCalls()) {
                var serializedToolCall = toolCalls.addObject()
                        .put("id", toolCall.id())
                        .put("type", toolCall.type());
                serializedToolCall.putObject("function")
                        .put("name", toolCall.function().name())
                        .put("arguments", toolCall.function().arguments());
            }
        }
    }

    private void appendToolResult(ArrayNode conversation, LlmToolCall toolCall, String output) {
        conversation.addObject()
                .put("role", "tool")
                .put("tool_call_id", toolCall.id())
                .put("content", output);
    }

    private ArrayNode createChatInput(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM chat message is required");
        }

        ArrayNode input = objectMapper.createArrayNode();
        for (LlmMessage message : messages) {
            if (message == null || !StringUtils.hasText(message.role()) || !StringUtils.hasText(message.content())) {
                throw new IllegalArgumentException("LLM chat messages require a role and content");
            }
            input.addObject()
                    .put("role", message.role())
                    .put("content", message.content());
        }
        return input;
    }

    private OpenAICompatibleChatRequest buildRequest(
            ArrayNode messages,
            List<OpenAICompatibleChatRequest.FunctionTool> tools,
            String instructions
    ) {
        boolean hasTools = !tools.isEmpty();

        if (StringUtils.hasText(instructions)) {
            ArrayNode messagesWithInstructions = objectMapper.createArrayNode();
            messagesWithInstructions.addObject()
                    .put("role", "system")
                    .put("content", instructions);
            messagesWithInstructions.addAll(messages);
            messages = messagesWithInstructions;
        }

        return new OpenAICompatibleChatRequest(
                settings.model(),
                messages,
                settings.maxOutputTokens(),
                StringUtils.hasText(settings.reasoningEffort())
                        ? settings.reasoningEffort()
                        : null,
                hasTools ? tools : null,
                hasTools ? settings.parallelToolCalls() : null
        );
    }

    private OpenAICompatibleChatResponse callChatCompletions(OpenAICompatibleChatRequest request) {
        OpenAICompatibleChatResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenAICompatibleChatResponse.class);
        requireCompleteResponse(response);
        return response;
    }

    private OpenAICompatibleChatRequest.FunctionTool toFunctionTool(LlmTool<?> function) {
        return new OpenAICompatibleChatRequest.FunctionTool(
                "function",
                new OpenAICompatibleChatRequest.FunctionDefinition(
                        function.name(),
                        function.description(),
                        jsonSchema.generate(function.parametersType()))
        );
    }

    private List<LlmToolCall> findToolCalls(OpenAICompatibleChatResponse response) {
        OpenAICompatibleChatResponse.Message message = firstChoice(response).message();
        if (message.toolCalls() == null) {
            return List.of();
        }
        return message.toolCalls().stream()
                .map(toolCall -> new LlmToolCall(
                        toolCall.id(),
                        toolCall.function().name(),
                        toolCall.function().arguments()
                ))
                .toList();
    }

    private String requireOutputText(OpenAICompatibleChatResponse response, boolean allowEmptyAfterToolCall) {
        OpenAICompatibleChatResponse.Message message = firstChoice(response).message();
        if (StringUtils.hasText(message.content())) {
            return message.content();
        }
        if (allowEmptyAfterToolCall) {
            return "";
        }
        throw new LlmException(settings.providerName() + " response did not contain output text");
    }

    private OpenAICompatibleChatResponse.Choice firstChoice(OpenAICompatibleChatResponse response) {
        requireCompleteResponse(response);
        if (response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst() == null
                || response.choices().getFirst().message() == null) {
            throw new LlmException(settings.providerName() + " response did not contain a message choice");
        }
        return response.choices().getFirst();
    }

    private void requireCompleteResponse(OpenAICompatibleChatResponse response) {
        if (response == null) {
            throw new LlmException(settings.providerName() + " returned an empty response");
        }
        if (!StringUtils.hasText(response.id())) {
            throw new LlmException(settings.providerName() + " response did not contain an id");
        }
        if (response.choices() == null) {
            throw new LlmException(settings.providerName() + " response did not contain choices");
        }
    }

    private <T> LlmResult<T> result(OpenAICompatibleChatResponse response, T output, UsageTotals usage) {
        return new LlmResult<>(
                response.id(),
                response.model(),
                output,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens()
        );
    }

    private void logCompletion(OpenAICompatibleChatResponse response) {
        OpenAICompatibleChatResponse.Usage usage = response.usage();
        log.info("{} Chat Completions API completed with responseId={}, inputTokens={}, outputTokens={}, model={}",
                settings.providerName(), response.id(), usage != null ? usage.promptTokens() : null,
                usage != null ? usage.completionTokens() : null, response.model());
    }

    private static final class UsageTotals {

        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private boolean present;

        private void add(OpenAICompatibleChatResponse.Usage usage) {
            if (usage == null) {
                return;
            }
            present = true;
            inputTokens += usage.promptTokens() != null ? usage.promptTokens() : 0;
            outputTokens += usage.completionTokens() != null ? usage.completionTokens() : 0;
            totalTokens += usage.totalTokens() != null ? usage.totalTokens() : 0;
        }

        private Long inputTokens() {
            return present ? inputTokens : null;
        }

        private Long outputTokens() {
            return present ? outputTokens : null;
        }

        private Long totalTokens() {
            return present ? totalTokens : null;
        }
    }
}
