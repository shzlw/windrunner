package com.windrunner.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.windrunner.server.llm.config.LlmExecutionConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentServiceTest {

    private ExecutorService agentExecutor;
    private ExecutorService toolExecutor;

    @BeforeEach
    void createExecutors() {
        LlmExecutionConfig config = new LlmExecutionConfig();
        agentExecutor = config.llmAgentExecutor();
        toolExecutor = config.llmToolExecutor();
    }

    @AfterEach
    void shutdownExecutors() {
        agentExecutor.close();
        toolExecutor.close();
    }

    private AgentService service() {
        return service(Duration.ofSeconds(5));
    }

    private AgentService service(Duration agentTimeout) {
        LlmProperties properties = new LlmProperties();
        properties.setAgentTimeout(agentTimeout);
        return new AgentService(new ObjectMapper(), properties, agentExecutor, toolExecutor);
    }

    @Test
    void failsImmediatelyWhenProviderRequestsAnUnavailableTool() {
        AgentService service = service();
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
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                    }
                }
        ))
                .isInstanceOf(LlmException.class)
                .hasMessage("TestProvider requested an unavailable tool: fetch_user_details. The request cannot be completed because that capability is not available.");

        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void executesToolsUntilProviderReturnsNoToolCalls() {
        AgentService service = service();
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
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                        results.forEach(result -> events.add(result.toolCall().id() + ":" + result.output()));
                    }
                }
        );

        assertThat(finalResponse.toolCalls()).isEmpty();
        assertThat(events).containsExactly("model", "call-1:seen:x");
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void executesParallelSafeToolsConcurrentlyAndAppendsResultsInCallOrder() {
        AgentService service = service();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        List<String> results = new ArrayList<>();

        LlmTool<Parameters> first = parallelTool("first", started, release, active, maximumActive);
        LlmTool<Parameters> second = parallelTool("second", started, release, active, maximumActive);

        service.<Response>run(
                "TestProvider",
                List.of(first, second),
                2,
                new AgentService.AgentLoop<>() {
                    @Override
                    public Response callModel() {
                        if (modelCalls.getAndIncrement() == 0) {
                            return new Response(List.of(
                                    new LlmToolCall("call-1", "first", "{\"value\":\"x\"}"),
                                    new LlmToolCall("call-2", "second", "{\"value\":\"y\"}")));
                        }
                        return new Response(List.of());
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(Response response) {
                        return response.toolCalls();
                    }

                    @Override
                    public void preserveModelResponse(Response response) {
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> toolResults) {
                        toolResults.forEach(result -> results.add(result.toolCall().id() + ":" + result.output()));
                    }
                }
        );

        assertThat(maximumActive).hasValue(2);
        assertThat(results).containsExactly("call-1:seen:x", "call-2:seen:y");
    }

    @Test
    void keepsAParallelSafeToolSequentialWhenTheBatchContainsAnUnsafeTool() throws Exception {
        AgentService service = service();
        CountDownLatch unsafeStarted = new CountDownLatch(1);
        CountDownLatch releaseUnsafe = new CountDownLatch(1);
        AtomicBoolean unsafeFinished = new AtomicBoolean();
        AtomicBoolean safeStartedTooEarly = new AtomicBoolean();
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        LlmTool<Parameters> unsafe = new LlmTool<>(
                "unsafe",
                "Sequential test tool",
                Parameters.class,
                ignored -> {
                    unsafeStarted.countDown();
                    releaseUnsafe.await(2, TimeUnit.SECONDS);
                    unsafeFinished.set(true);
                    return "unsafe-done";
                });
        LlmTool<Parameters> safe = new LlmTool<>(
                "safe",
                "Parallel test tool",
                Parameters.class,
                ignored -> {
                    if (!unsafeFinished.get()) {
                        safeStartedTooEarly.set(true);
                    }
                    return "safe-done";
                },
                true);

        Thread runner = Thread.startVirtualThread(() -> {
            try {
                service.<Response>run(
                        "TestProvider",
                        List.of(unsafe, safe),
                        2,
                        new AgentService.AgentLoop<>() {
                            @Override
                            public Response callModel() {
                                if (modelCalls.getAndIncrement() == 0) {
                                    return new Response(List.of(
                                            new LlmToolCall("call-1", "unsafe", "{}"),
                                            new LlmToolCall("call-2", "safe", "{}")));
                                }
                                return new Response(List.of());
                            }

                            @Override
                            public List<LlmToolCall> findToolCalls(Response response) {
                                return response.toolCalls();
                            }

                            @Override
                            public void preserveModelResponse(Response response) {
                            }

                            @Override
                            public void appendToolResults(List<AgentService.ToolResult> results) {
                            }
                        });
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        assertThat(unsafeStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(safeStartedTooEarly).isFalse();
        releaseUnsafe.countDown();
        runner.join(1_000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(failure).hasValue(null);
        assertThat(safeStartedTooEarly).isFalse();
    }

    @Test
    void limitsParallelSubmissionWindowToFourTools() throws Exception {
        AgentService service = service();
        CountDownLatch firstWaveStarted = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        List<String> results = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        LlmTool<Parameters> tool = new LlmTool<>(
                "bounded",
                "Bounded parallel test tool",
                Parameters.class,
                ignored -> {
                    int current = active.incrementAndGet();
                    maximumActive.accumulateAndGet(current, Math::max);
                    firstWaveStarted.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("Parallel tool batch did not start four tools");
                        }
                        return "done";
                    } finally {
                        active.decrementAndGet();
                    }
                },
                true);

        List<LlmToolCall> calls = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            calls.add(new LlmToolCall("call-" + index, "bounded", "{}"));
        }

        Thread runner = Thread.startVirtualThread(() -> {
            try {
                service.<Response>run(
                        "TestProvider",
                        List.of(tool),
                        2,
                        new AgentService.AgentLoop<>() {
                            @Override
                            public Response callModel() {
                                if (modelCalls.getAndIncrement() == 0) {
                                    return new Response(calls);
                                }
                                return new Response(List.of());
                            }

                            @Override
                            public List<LlmToolCall> findToolCalls(Response response) {
                                return response.toolCalls();
                            }

                            @Override
                            public void preserveModelResponse(Response response) {
                            }

                            @Override
                            public void appendToolResults(List<AgentService.ToolResult> toolResults) {
                                toolResults.forEach(result -> results.add(result.output()));
                            }
                        }
                );
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        assertThat(firstWaveStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(((ThreadPoolExecutor) toolExecutor).getQueue()).isEmpty();
        release.countDown();
        runner.join(2_000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(failure).hasValue(null);
        assertThat(maximumActive).hasValue(4);
        assertThat(results).hasSize(16);
    }

    @Test
    void cancelsOtherParallelToolsWhenOneToolFails() throws Exception {
        AgentService service = service();
        CountDownLatch blockingStarted = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger modelCalls = new AtomicInteger();

        LlmTool<Parameters> blocking = new LlmTool<>(
                "blocking",
                "Blocking parallel test tool",
                Parameters.class,
                ignored -> {
                    blockingStarted.countDown();
                    try {
                        new CountDownLatch(1).await();
                        return "unexpected";
                    } catch (InterruptedException exception) {
                        cancelled.countDown();
                        throw exception;
                    }
                },
                true);
        LlmTool<Parameters> failing = new LlmTool<>(
                "failing",
                "Failing parallel test tool",
                Parameters.class,
                ignored -> {
                    if (!blockingStarted.await(1, TimeUnit.SECONDS)) {
                        throw new AssertionError("Blocking tool did not start");
                    }
                    throw new IllegalStateException("boom");
                },
                true);

        assertThatThrownBy(() -> service.<Response>run(
                "TestProvider",
                List.of(blocking, failing),
                2,
                new AgentService.AgentLoop<>() {
                    @Override
                    public Response callModel() {
                        if (modelCalls.getAndIncrement() == 0) {
                            return new Response(List.of(
                                    new LlmToolCall("call-1", "blocking", "{}"),
                                    new LlmToolCall("call-2", "failing", "{}")));
                        }
                        return new Response(List.of());
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(Response response) {
                        return response.toolCalls();
                    }

                    @Override
                    public void preserveModelResponse(Response response) {
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                    }
                }))
                .isInstanceOf(LlmException.class)
                .hasMessage("TestProvider tool execution failed: failing");

        assertThat(cancelled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void timesOutAndCancelsAStalledParallelBatch() throws Exception {
        AgentService service = service();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch cancelled = new CountDownLatch(2);
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        LlmTool<Parameters> stalled = new LlmTool<>(
                "stalled",
                "Stalled parallel test tool",
                Parameters.class,
                ignored -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                        return "unexpected";
                    } catch (InterruptedException exception) {
                        cancelled.countDown();
                        throw exception;
                    }
                },
                true);

        Thread runner = Thread.startVirtualThread(() -> {
            try {
                service.<Response>run(
                        "TestProvider",
                        List.of(stalled),
                        2,
                        Duration.ofMillis(100),
                        new AgentService.AgentLoop<>() {
                            @Override
                            public Response callModel() {
                                if (modelCalls.getAndIncrement() == 0) {
                                    return new Response(List.of(
                                            new LlmToolCall("call-1", "stalled", "{}"),
                                            new LlmToolCall("call-2", "stalled", "{}")));
                                }
                                return new Response(List.of());
                            }

                            @Override
                            public List<LlmToolCall> findToolCalls(Response response) {
                                return response.toolCalls();
                            }

                            @Override
                            public void preserveModelResponse(Response response) {
                            }

                            @Override
                            public void appendToolResults(List<AgentService.ToolResult> results) {
                            }
                        });
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        runner.join(1_000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(failure).hasValueSatisfying(throwable -> assertThat(throwable)
                .isInstanceOf(LlmException.class)
                .hasMessage("TestProvider parallel tool execution timed out after PT0.1S"));
        assertThat(cancelled.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void timesOutAndInterruptsTheWholeAgentRun() throws Exception {
        AgentService service = service(Duration.ofMillis(100));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        LlmTool<Parameters> tool = new LlmTool<>(
                "echo",
                "Echo test input",
                Parameters.class,
                parameters -> parameters.value());

        assertThatThrownBy(() -> service.<Response>run(
                "TestProvider",
                List.of(tool),
                1,
                new AgentService.AgentLoop<>() {
                    @Override
                    public Response callModel() {
                        started.countDown();
                        try {
                            new CountDownLatch(1).await();
                            throw new AssertionError("Agent model call should have been interrupted");
                        } catch (InterruptedException exception) {
                            cancelled.countDown();
                            Thread.currentThread().interrupt();
                            throw new LlmException("Model call interrupted", exception);
                        }
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(Response response) {
                        return response.toolCalls();
                    }

                    @Override
                    public void preserveModelResponse(Response response) {
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                    }
                }))
                .isInstanceOf(LlmException.class)
                .hasMessage("TestProvider agent execution timed out after PT0.1S");

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(cancelled.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void removesATimedOutAgentThatHasNotStartedFromTheQueue() throws Exception {
        agentExecutor.close();
        agentExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        agentExecutor.submit(() -> {
            blockerStarted.countDown();
            releaseBlocker.await();
            return null;
        });
        assertThat(blockerStarted.await(1, TimeUnit.SECONDS)).isTrue();

        try {
            AgentService service = service(Duration.ofMillis(100));
            LlmTool<Parameters> tool = new LlmTool<>(
                    "echo",
                    "Echo test input",
                    Parameters.class,
                    parameters -> parameters.value());

            assertThatThrownBy(() -> service.<Response>run(
                    "TestProvider",
                    List.of(tool),
                    1,
                    new AgentService.AgentLoop<>() {
                        @Override
                        public Response callModel() {
                            return new Response(List.of());
                        }

                        @Override
                        public List<LlmToolCall> findToolCalls(Response response) {
                            return response.toolCalls();
                        }

                        @Override
                        public void preserveModelResponse(Response response) {
                        }

                        @Override
                        public void appendToolResults(List<AgentService.ToolResult> results) {
                        }
                    }))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("TestProvider agent execution timed out after PT0.1S");

            assertThat(((ThreadPoolExecutor) agentExecutor).getQueue()).isEmpty();
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void rejectsMoreThanSixteenToolCallsInOneRound() {
        AgentService service = service();
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        LlmTool<Parameters> tool = new LlmTool<>(
                "bounded",
                "Bounded call-count test tool",
                Parameters.class,
                ignored -> {
                    executions.incrementAndGet();
                    return "done";
                },
                true);
        List<LlmToolCall> calls = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            calls.add(new LlmToolCall("call-" + index, "bounded", "{}"));
        }

        assertThatThrownBy(() -> service.<Response>run(
                "TestProvider",
                List.of(tool),
                2,
                new AgentService.AgentLoop<>() {
                    @Override
                    public Response callModel() {
                        modelCalls.incrementAndGet();
                        return new Response(calls);
                    }

                    @Override
                    public List<LlmToolCall> findToolCalls(Response response) {
                        return response.toolCalls();
                    }

                    @Override
                    public void preserveModelResponse(Response response) {
                    }

                    @Override
                    public void appendToolResults(List<AgentService.ToolResult> results) {
                    }
                }))
                .isInstanceOf(LlmException.class)
                .hasMessage("TestProvider exceeded the maximum tool calls in one round: 16");

        assertThat(modelCalls).hasValue(1);
        assertThat(executions).hasValue(0);
    }

    private LlmTool<Parameters> parallelTool(
            String name,
            CountDownLatch started,
            CountDownLatch release,
            AtomicInteger active,
            AtomicInteger maximumActive
    ) {
        return new LlmTool<>(
                name,
                "Parallel test tool",
                Parameters.class,
                parameters -> {
                    int current = active.incrementAndGet();
                    maximumActive.accumulateAndGet(current, Math::max);
                    started.countDown();
                    if (started.getCount() == 0) {
                        release.countDown();
                    }
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("Tools did not execute concurrently");
                        }
                        return "seen:" + parameters.value();
                    } finally {
                        active.decrementAndGet();
                    }
                },
                true
        );
    }

    private record Response(List<LlmToolCall> toolCalls) {
    }

    private record Parameters(String value) {
    }
}
