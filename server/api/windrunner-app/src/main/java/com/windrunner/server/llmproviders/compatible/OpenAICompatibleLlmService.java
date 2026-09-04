package com.windrunner.server.llmproviders.compatible;

import com.windrunner.server.llm.*;
import com.windrunner.server.llmproviders.openai.client.OpenAIJsonSchema;
import com.windrunner.server.llmproviders.openai.client.OpenAIResponse;
import com.windrunner.server.llmproviders.openai.client.OpenAIResponseRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        List<OpenAIResponseRequest.FunctionTool> responseTools = safeTools.stream()
                .map(this::toFunctionTool)
                .toList();

        AtomicReference<ArrayNode> pendingInput = new AtomicReference<>(input);
        AtomicReference<String> previousResponseId = new AtomicReference<>();
        UsageTotals usageTotals = new UsageTotals();
        AtomicBoolean executedTool = new AtomicBoolean();

        OpenAIResponse response = agentService.run(
                settings.providerName(),
                safeTools,
                settings.maxToolRounds(),
                settings.parallelToolTimeout(),
                new AgentService.AgentLoop<>() {
                    @Override
                    public OpenAIResponse callModel() {
                        OpenAIResponse response = callResponsesApi(
                                buildRequest(
                                        pendingInput.get(),
                                        responseTools,
                                        instructions,
                                        previousResponseId.get())
                        );
                        previousResponseId.set(response.id());
                        pendingInput.set(objectMapper.createArrayNode());
                        usageTotals.add(response.usage());
                        return response;
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(OpenAIResponse response) {
                        return OpenAICompatibleLlmService.this.findToolCalls(response);
                    }

                    @Override
                    public void preserveModelResponse(OpenAIResponse response) {
                        // Response state is maintained server-side via previous_response_id.
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                        executedTool.set(!results.isEmpty());
                        results.forEach(result -> appendFunctionCallOutput(
                                pendingInput.get(), result.toolCall(), result.output()));
                    }
                }
        );

        String output = requireOutputText(response, executedTool.get());
        logCompletion(response);
        return result(response, output, usageTotals);
    }

    private void appendFunctionCallOutput(ArrayNode conversationInput, LlmToolCall toolCall, String output) {
        conversationInput.addObject()
                .put("type", "function_call_output")
                .put("call_id", toolCall.id())
                .put("output", output);
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

    private OpenAIResponseRequest buildRequest(
            Object input,
            List<OpenAIResponseRequest.FunctionTool> tools,
            String instructions,
            String previousResponseId
    ) {
        boolean hasTools = !tools.isEmpty();

        return new OpenAIResponseRequest(
                settings.model(),
                input,
                StringUtils.hasText(instructions) ? instructions : null,
                settings.maxOutputTokens(),
                StringUtils.hasText(settings.reasoningEffort())
                        ? new OpenAIResponseRequest.Reasoning(settings.reasoningEffort())
                        : null,
                hasTools ? tools : null,
                hasTools ? settings.parallelToolCalls() : null,
                previousResponseId
        );
    }

    private OpenAIResponse callResponsesApi(OpenAIResponseRequest request) {
        OpenAIResponse response = restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenAIResponse.class);
        requireCompleteResponse(response);
        return response;
    }

    private OpenAIResponseRequest.FunctionTool toFunctionTool(LlmTool<?> function) {
        return new OpenAIResponseRequest.FunctionTool(
                "function",
                function.name(),
                function.description(),
                true,
                jsonSchema.generate(function.parametersType())
        );
    }

    private List<LlmToolCall> findToolCalls(OpenAIResponse response) {
        return response.output().stream()
                .filter(outputItem -> "function_call".equals(outputItem.path("type").asString()))
                .map(outputItem -> new LlmToolCall(
                        outputItem.path("call_id").asString(),
                        outputItem.path("name").asString(),
                        outputItem.path("arguments").asString()
                ))
                .toList();
    }

    private String requireOutputText(OpenAIResponse response, boolean allowEmptyAfterToolCall) {
        for (JsonNode outputItem : response.output()) {
            JsonNode content = outputItem.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                String type = contentItem.path("type").asString();
                if ("refusal".equals(type) && StringUtils.hasText(contentItem.path("refusal").asString())) {
                    throw new LlmException(
                            settings.providerName() + " refused the request: "
                                    + contentItem.path("refusal").asString()
                    );
                }
                if ("output_text".equals(type) && StringUtils.hasText(contentItem.path("text").asString())) {
                    return contentItem.path("text").asString();
                }
            }
        }
        if (allowEmptyAfterToolCall) {
            return "";
        }
        throw new LlmException(settings.providerName() + " response did not contain output text");
    }

    private void requireCompleteResponse(OpenAIResponse response) {
        if (response == null) {
            throw new LlmException(settings.providerName() + " returned an empty response");
        }
        if (!StringUtils.hasText(response.id())) {
            throw new LlmException(settings.providerName() + " response did not contain an id");
        }
        if ("incomplete".equals(response.status())) {
            String reason = response.incompleteDetails() != null
                    ? response.incompleteDetails().reason()
                    : "unknown reason";
            throw new LlmException(settings.providerName() + " response was incomplete: " + reason);
        }
        if (response.output() == null) {
            throw new LlmException(settings.providerName() + " response did not contain output");
        }
    }

    private <T> LlmResult<T> result(OpenAIResponse response, T output, UsageTotals usage) {
        return new LlmResult<>(
                response.id(),
                response.model(),
                output,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens()
        );
    }

    private void logCompletion(OpenAIResponse response) {
        OpenAIResponse.Usage usage = response.usage();
        log.info("{} Responses API completed with responseId={}, inputTokens={}, outputTokens={}, model={}",
                settings.providerName(), response.id(), usage != null ? usage.inputTokens() : null,
                usage != null ? usage.outputTokens() : null, response.model());
    }

    private static final class UsageTotals {

        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private boolean present;

        private void add(OpenAIResponse.Usage usage) {
            if (usage == null) {
                return;
            }
            present = true;
            inputTokens += usage.inputTokens() != null ? usage.inputTokens() : 0;
            outputTokens += usage.outputTokens() != null ? usage.outputTokens() : 0;
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
