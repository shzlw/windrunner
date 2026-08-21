package com.windrunner.server.llmproviders.openai;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmException;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.llm.LlmToolCall;
import com.windrunner.server.llmproviders.openai.client.OpenAIJsonSchema;
import com.windrunner.server.llmproviders.openai.client.OpenAIResponse;
import com.windrunner.server.llmproviders.openai.client.OpenAIResponseRequest;
import com.windrunner.server.llmproviders.openai.config.OpenAIProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class OpenAIService implements LlmService {

    private final RestClient restClient;
    private final OpenAIProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenAIJsonSchema jsonSchema;
    private final AgentService agentService;

    public OpenAIService(
            RestClient restClient,
            OpenAIProperties properties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.restClient = restClient;
        this.properties = properties;
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
        List<OpenAIResponseRequest.FunctionTool> openAITools = safeTools.stream()
                .map(this::toFunctionTool)
                .toList();

        ArrayNode conversationInput = input.deepCopy();
        UsageTotals usageTotals = new UsageTotals();
        AtomicBoolean executedTool = new AtomicBoolean();

        OpenAIResponse response = agentService.run(
                "OpenAI",
                safeTools,
                properties.getMaxToolRounds(),
                new AgentService.AgentLoop<>() {
                    @Override
                    public OpenAIResponse callModel() {
                        OpenAIResponse response = callOpenAI(
                                buildRequest(conversationInput, openAITools, instructions)
                        );
                        usageTotals.add(response.usage());
                        return response;
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(OpenAIResponse response) {
                        return OpenAIService.this.findToolCalls(response);
                    }

                    @Override
                    public void preserveModelResponse(OpenAIResponse response) {
                        preserveOutput(response, conversationInput);
                    }

                    @Override
                    public void appendToolResult(LlmToolCall toolCall, String output) {
                        executedTool.set(true);
                        appendFunctionCallOutput(conversationInput, toolCall, output);
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
            String instructions
    ) {
        boolean hasTools = !tools.isEmpty();

        return new OpenAIResponseRequest(
                properties.getModel(),
                input,
                StringUtils.hasText(instructions) ? instructions : null,
                properties.getMaxOutputTokens(),
                StringUtils.hasText(properties.getReasoningEffort())
                        ? new OpenAIResponseRequest.Reasoning(properties.getReasoningEffort())
                        : null,
                hasTools ? tools : null,
                hasTools ? Boolean.FALSE : null
        );
    }

    private OpenAIResponse callOpenAI(OpenAIResponseRequest request) {
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

    private void preserveOutput(OpenAIResponse response, ArrayNode conversationInput) {
        response.output().forEach(outputItem -> conversationInput.add(outputItem.deepCopy()));
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
                            "OpenAI refused the request: " + contentItem.path("refusal").asString()
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
        throw new LlmException("OpenAI response did not contain output text");
    }

    private void requireCompleteResponse(OpenAIResponse response) {
        if (response == null) {
            throw new LlmException("OpenAI returned an empty response");
        }
        if ("incomplete".equals(response.status())) {
            String reason = response.incompleteDetails() != null
                    ? response.incompleteDetails().reason()
                    : "unknown reason";
            throw new LlmException("OpenAI response was incomplete: " + reason);
        }
        if (response.output() == null) {
            throw new LlmException("OpenAI response did not contain output");
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
        log.info("OpenAI Responses API completed with responseId={}, inputTokens={}, outputTokens={}",
                response.id(), usage != null ? usage.inputTokens() : null,
                usage != null ? usage.outputTokens() : null);
    }

    private static final class UsageTotals {

        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private boolean present;

        private UsageTotals() {
        }

        private UsageTotals(OpenAIResponse.Usage usage) {
            add(usage);
        }

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
