package com.windrunner.server.llmproviders.gemini;

import com.windrunner.server.llm.*;
import com.windrunner.server.llmproviders.gemini.client.GeminiJsonSchema;
import com.windrunner.server.llmproviders.gemini.client.GeminiRequest;
import com.windrunner.server.llmproviders.gemini.client.GeminiResponse;
import com.windrunner.server.llmproviders.gemini.config.GeminiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class GeminiService implements LlmService {

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final GeminiJsonSchema jsonSchema;
    private final AgentService agentService;

    public GeminiService(
            RestClient restClient,
            GeminiProperties properties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonSchema = new GeminiJsonSchema(objectMapper);
        this.agentService = agentService;
    }

    @Override
    public LlmResult<String> runChatWithTools(
            List<LlmMessage> messages,
            String instructions,
            List<LlmTool<?>> tools
    ) {
        return runToolLoop(createInitialStepInputs(messages), instructions, tools);
    }

    private LlmResult<String> runToolLoop(
            List<GeminiRequest.StepInput> initialInputs,
            String instructions,
            List<LlmTool<?>> tools
    ) {
        List<LlmTool<?>> safeTools = tools == null ? List.of() : tools;
        List<GeminiRequest.Tool> geminiTools = safeTools.stream()
                .map(this::toGeminiTool)
                .toList();

        UsageTotals usageTotals = new UsageTotals();
        AtomicBoolean executedTool = new AtomicBoolean();
        AtomicReference<String> previousInteractionId = new AtomicReference<>();
        AtomicReference<LlmToolCall> pendingToolCall = new AtomicReference<>();
        AtomicReference<String> pendingToolOutput = new AtomicReference<>();

        GeminiResponse response = agentService.run(
                "Gemini",
                safeTools,
                properties.getMaxToolRounds(),
                new AgentService.AgentLoop<>() {
                    @Override
                    public GeminiResponse callModel() {
                        GeminiRequest request;
                        if (previousInteractionId.get() != null && pendingToolCall.get() != null) {
                            LlmToolCall toolCall = pendingToolCall.get();
                            String toolOutput = pendingToolOutput.get();
                            GeminiRequest.StepInput functionResult = GeminiRequest.StepInput.functionResult(
                                    toolCall.id(),
                                    toolCall.name(),
                                    toolOutput
                            );
                            request = buildToolResultRequest(previousInteractionId.get(), functionResult, instructions, geminiTools);
                        } else {
                            request = buildInitialRequest(initialInputs, instructions, geminiTools);
                        }

                        GeminiResponse response = callGemini(request);
                        if (StringUtils.hasText(response.id())) {
                            previousInteractionId.set(response.id());
                        }
                        usageTotals.add(response.usage());
                        return response;
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(GeminiResponse response) {
                        return GeminiService.this.findToolCalls(response);
                    }

                    @Override
                    public void preserveModelResponse(GeminiResponse response) {
                        // Response state is maintained server-side via previous_interaction_id
                    }

                    @Override
                    public void appendToolResult(LlmToolCall toolCall, String output) {
                        executedTool.set(true);
                        pendingToolCall.set(toolCall);
                        pendingToolOutput.set(output);
                    }
                }
        );

        String output = requireOutputText(response, executedTool.get());
        logCompletion(response);
        return result(response, output, usageTotals);
    }

    private List<GeminiRequest.StepInput> createInitialStepInputs(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM chat message is required");
        }

        return messages.stream().map(message -> {
            if (message == null || !StringUtils.hasText(message.role()) || !StringUtils.hasText(message.content())) {
                throw new IllegalArgumentException("LLM chat messages require a role and content");
            }

            if ("assistant".equals(message.role()) || "model".equals(message.role())) {
                return GeminiRequest.StepInput.modelOutput(message.content());
            } else {
                return GeminiRequest.StepInput.userInput(message.content());
            }
        }).toList();
    }

    private GeminiRequest buildInitialRequest(
            List<GeminiRequest.StepInput> inputs,
            String instructions,
            List<GeminiRequest.Tool> tools
    ) {
        GeminiRequest.Config config = new GeminiRequest.Config(
                properties.getMaxOutputTokens(),
                properties.getTemperature()
        );

        List<GeminiRequest.Tool> geminiTools = !tools.isEmpty() ? tools : null;
        Object inputPayload = inputs.size() == 1 && "user_input".equals(inputs.get(0).type())
                ? inputs.get(0).content().get(0).text()
                : inputs;

        return new GeminiRequest(
                properties.getModel(),
                StringUtils.hasText(instructions) ? instructions : null,
                inputPayload,
                geminiTools,
                config,
                null
        );
    }

    private GeminiRequest buildToolResultRequest(
            String previousInteractionId,
            GeminiRequest.StepInput functionResultInput,
            String instructions,
            List<GeminiRequest.Tool> tools
    ) {
        GeminiRequest.Config config = new GeminiRequest.Config(
                properties.getMaxOutputTokens(),
                properties.getTemperature()
        );

        List<GeminiRequest.Tool> geminiTools = !tools.isEmpty() ? tools : null;

        return new GeminiRequest(
                properties.getModel(),
                StringUtils.hasText(instructions) ? instructions : null,
                List.of(functionResultInput),
                geminiTools,
                config,
                previousInteractionId
        );
    }

    private GeminiResponse callGemini(GeminiRequest request) {
        GeminiResponse response = restClient.post()
                .uri("/interactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        requireCompleteResponse(response);
        return response;
    }

    private GeminiRequest.Tool toGeminiTool(LlmTool<?> tool) {
        ObjectNode schema = jsonSchema.generate(tool.parametersType());
        return GeminiRequest.Tool.function(tool.name(), tool.description(), schema);
    }

    private List<LlmToolCall> findToolCalls(GeminiResponse response) {
        List<LlmToolCall> toolCalls = new ArrayList<>();

        if (response.steps() == null || response.steps().isEmpty()) {
            return toolCalls;
        }

        for (GeminiResponse.Step step : response.steps()) {
            if ("function_call".equals(step.type())) {
                String argumentsJson = objectMapper.writeValueAsString(step.arguments());
                String callId = StringUtils.hasText(step.id())
                        ? step.id()
                        : (StringUtils.hasText(step.callId()) ? step.callId() : "gemini-" + System.nanoTime());
                toolCalls.add(new LlmToolCall(callId, step.name(), argumentsJson));
            }
        }

        return toolCalls;
    }

    private String requireOutputText(GeminiResponse response, boolean allowEmptyAfterToolCall) {
        if (response.steps() == null || response.steps().isEmpty()) {
            if (allowEmptyAfterToolCall) {
                return "";
            }
            throw new LlmException("Gemini response did not contain steps");
        }

        for (GeminiResponse.Step step : response.steps()) {
            if ("model_output".equals(step.type()) && step.content() != null) {
                for (GeminiResponse.ContentPart contentPart : step.content()) {
                    if ("text".equals(contentPart.type()) && StringUtils.hasText(contentPart.text())) {
                        return contentPart.text();
                    }
                }
            }
        }

        if (allowEmptyAfterToolCall) {
            return "";
        }

        throw new LlmException("Gemini response did not contain text output");
    }

    private void requireCompleteResponse(GeminiResponse response) {
        if (response == null) {
            throw new LlmException("Gemini returned an empty response");
        }

        if (response.error() != null && StringUtils.hasText(response.error().message())) {
            throw new LlmException("Gemini error: " + response.error().message());
        }

        String status = response.status();
        if ("failed".equals(status)) {
            throw new LlmException("Gemini interaction failed");
        }
    }

    private <T> LlmResult<T> result(GeminiResponse response, T output, UsageTotals usage) {
        return new LlmResult<>(
                response.id() != null ? response.id() : "gemini-" + System.nanoTime(),
                properties.getModel(),
                output,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens()
        );
    }

    private void logCompletion(GeminiResponse response) {
        GeminiResponse.Usage usage = response.usage();
        log.info("Gemini completed with inputTokens={}, outputTokens={}, totalTokens={}",
                usage != null ? usage.inputTokens() : null,
                usage != null ? usage.outputTokens() : null,
                usage != null ? usage.totalTokens() : null);
    }

    private static final class UsageTotals {

        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private boolean present;

        void add(GeminiResponse.Usage usage) {
            if (usage != null) {
                this.inputTokens += usage.inputTokens() != null ? usage.inputTokens() : 0;
                this.outputTokens += usage.outputTokens() != null ? usage.outputTokens() : 0;
                this.totalTokens += usage.totalTokens() != null ? usage.totalTokens() : 0;
                this.present = true;
            }
        }

        Long inputTokens() {
            return present ? inputTokens : null;
        }

        Long outputTokens() {
            return present ? outputTokens : null;
        }

        Long totalTokens() {
            return present ? totalTokens : null;
        }
    }
}
