package com.windrunner.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentServiceTest {

    @Test
    void failsImmediatelyWhenProviderRequestsAnUnavailableTool() {
        AgentService service = new AgentService(new ObjectMapper());
        AtomicInteger modelCalls = new AtomicInteger();

        assertThatThrownBy(() -> service.<Response>run(
                "TestProvider",
                List.of(new LlmTool<>(
                        "echo",
                        "Echo test input",
                        Parameters.class,
                        parameters -> "seen:" + parameters.value()
                )),
                2,
                new AgentService.AgentLoop<>() {
                    @Override
                    public Response callModel() {
                        modelCalls.incrementAndGet();
                        return new Response(List.of(new LlmToolCall("call-1", "fetch_user_details", "{}")));
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(Response response) {
                        return response.toolCalls();
                    }

                    @Override
                    public void preserveModelResponse(Response response) {
                    }

                    @Override
                    public void appendToolResult(LlmToolCall toolCall, String output) {
                    }
                }
        ))
                .isInstanceOf(LlmException.class)
                .hasMessage("TestProvider requested an unavailable tool: fetch_user_details. The request cannot be completed because that capability is not available.");

        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void executesToolsUntilProviderReturnsNoToolCalls() {
        AgentService service = new AgentService(new ObjectMapper());
        List<String> events = new ArrayList<>();
        AtomicInteger modelCalls = new AtomicInteger();
        LlmTool<Parameters> tool = new LlmTool<>(
                "echo",
                "Echo test input",
                Parameters.class,
                parameters -> "seen:" + parameters.value()
        );

        Response finalResponse = service.run(
                "TestProvider",
                List.of(tool),
                2,
                new AgentService.AgentLoop<>() {
                    @Override
                    public Response callModel() {
                        if (modelCalls.getAndIncrement() == 0) {
                            return new Response(List.of(new LlmToolCall("call-1", "echo", "{\"value\":\"x\"}")));
                        }
                        return new Response(List.of());
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(Response response) {
                        return response.toolCalls();
                    }

                    @Override
                    public void preserveModelResponse(Response response) {
                        events.add("model");
                    }

                    @Override
                    public void appendToolResult(LlmToolCall toolCall, String output) {
                        events.add(toolCall.id() + ":" + output);
                    }
                }
        );

        assertThat(finalResponse.toolCalls()).isEmpty();
        assertThat(events).containsExactly("model", "call-1:seen:x");
        assertThat(modelCalls).hasValue(2);
    }

    private record Response(List<LlmToolCall> toolCalls) {
    }

    private record Parameters(String value) {
    }
}
