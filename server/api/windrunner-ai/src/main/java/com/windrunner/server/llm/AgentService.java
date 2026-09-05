package com.windrunner.server.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class AgentService {

    private static final int MAX_IN_FLIGHT_TOOL_CALLS = 4;
    private static final int MAX_TOOL_CALLS_PER_ROUND = 16;
    private static final Duration DEFAULT_PARALLEL_TOOL_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final LlmProperties properties;
    private final ExecutorService agentExecutor;
    private final ExecutorService toolExecutor;

    public AgentService(
            ObjectMapper objectMapper,
            LlmProperties properties,
            @Qualifier("llmAgentExecutor") ExecutorService agentExecutor,
            @Qualifier("llmToolExecutor") ExecutorService toolExecutor
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
    }

    public <R> R run(
            String providerName,
            List<LlmTool<?>> tools,
            int maxToolRounds,
            AgentLoop<R> loop
    ) {
        return run(providerName, tools, maxToolRounds, DEFAULT_PARALLEL_TOOL_TIMEOUT, loop);
    }

    public <R> R run(
            String providerName,
            List<LlmTool<?>> tools,
            int maxToolRounds,
            Duration parallelToolTimeout,
            AgentLoop<R> loop
    ) {
        if (maxToolRounds < 1) {
            throw new IllegalArgumentException(providerName + " max tool rounds must be at least 1");
        }
        long parallelToolTimeoutNanos = requirePositiveDuration(
                providerName, "parallel tool timeout", parallelToolTimeout);
        Duration agentTimeout = properties.getAgentTimeout();
        long agentTimeoutNanos = requirePositiveDuration(providerName, "agent timeout", agentTimeout);
        Objects.requireNonNull(loop, "Agent loop is required");
        Map<String, LlmTool<?>> toolsByName = validateTools(tools);

        long agentStartedNanos = System.nanoTime();
        Future<R> agentFuture;
        try {
            agentFuture = agentExecutor.submit(() -> runLoop(
                    providerName,
                    maxToolRounds,
                    parallelToolTimeout,
                    parallelToolTimeoutNanos,
                    agentTimeout,
                    agentTimeoutNanos,
                    agentStartedNanos,
                    toolsByName,
                    loop));
        } catch (RejectedExecutionException exception) {
            throw new LlmBusyException(providerName + " agent execution capacity is full", exception);
        }

        try {
            return agentFuture.get(agentTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            cancelAgent(agentFuture);
            throw agentTimeout(providerName, agentTimeout, exception);
        } catch (InterruptedException exception) {
            cancelAgent(agentFuture);
            Thread.currentThread().interrupt();
            throw new LlmException(providerName + " agent execution was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new LlmException(providerName + " agent execution failed", cause);
        } catch (CancellationException exception) {
            throw new LlmException(providerName + " agent execution was cancelled", exception);
        }
    }

    private void cancelAgent(Future<?> future) {
        future.cancel(true);
        if (agentExecutor instanceof ThreadPoolExecutor executor && future instanceof Runnable task) {
            executor.remove(task);
        }
    }

    private <R> R runLoop(
            String providerName,
            int maxToolRounds,
            Duration parallelToolTimeout,
            long parallelToolTimeoutNanos,
            Duration agentTimeout,
            long agentTimeoutNanos,
            long agentStartedNanos,
            Map<String, LlmTool<?>> toolsByName,
            AgentLoop<R> loop
    ) {
        int toolRounds = 0;
        while (true) {
            requireAgentActive(providerName, agentTimeout, agentTimeoutNanos, agentStartedNanos);
            R response = loop.callModel();
            requireAgentActive(providerName, agentTimeout, agentTimeoutNanos, agentStartedNanos);
            List<LlmToolCall> toolCalls = loop.findToolCalls(response);

            if (toolCalls == null || toolCalls.isEmpty()) {
                return response;
            }

            toolRounds++;
            if (toolRounds > maxToolRounds) {
                throw new LlmException(providerName + " exceeded the maximum tool rounds: " + maxToolRounds);
            }
            if (toolCalls.size() > MAX_TOOL_CALLS_PER_ROUND) {
                throw new LlmException(providerName + " exceeded the maximum tool calls in one round: "
                        + MAX_TOOL_CALLS_PER_ROUND);
            }

            loop.preserveModelResponse(response);
            List<ResolvedToolCall> resolvedCalls = resolveToolCalls(providerName, toolCalls, toolsByName);
            loop.appendToolResults(executeTools(
                    providerName,
                    toolRounds,
                    parallelToolTimeout,
                    parallelToolTimeoutNanos,
                    agentTimeout,
                    agentTimeoutNanos,
                    agentStartedNanos,
                    resolvedCalls));
        }
    }

    private List<ResolvedToolCall> resolveToolCalls(
            String providerName,
            List<LlmToolCall> toolCalls,
            Map<String, LlmTool<?>> toolsByName
    ) {
        List<ResolvedToolCall> resolvedCalls = new ArrayList<>(toolCalls.size());
        for (LlmToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                throw new LlmException(providerName + " returned an empty tool call");
            }
            LlmTool<?> tool = toolsByName.get(toolCall.name());
            if (tool == null) {
                log.warn("LLM requested unavailable tool provider={}, name={}, availableTools={}",
                        providerName, toolCall.name(), toolsByName.keySet());
                throw new LlmException(providerName + " requested an unavailable tool: " + toolCall.name()
                        + ". The request cannot be completed because that capability is not available.");
            }
            resolvedCalls.add(new ResolvedToolCall(toolCall, tool));
        }
        return resolvedCalls;
    }

    private List<ToolResult> executeTools(
            String providerName,
            int toolRound,
            Duration parallelToolTimeout,
            long parallelToolTimeoutNanos,
            Duration agentTimeout,
            long agentTimeoutNanos,
            long agentStartedNanos,
            List<ResolvedToolCall> calls
    ) {
        boolean canRunInParallel = calls.size() > 1
                && calls.stream().allMatch(call -> call.tool().parallelSafe());
        if (!canRunInParallel) {
            List<ToolResult> results = new ArrayList<>(calls.size());
            for (ResolvedToolCall call : calls) {
                requireAgentActive(providerName, agentTimeout, agentTimeoutNanos, agentStartedNanos);
                results.add(executeResolvedTool(providerName, toolRound, call));
            }
            return List.copyOf(results);
        }

        long agentRemainingNanos = requireAgentActive(
                providerName, agentTimeout, agentTimeoutNanos, agentStartedNanos);
        long timeoutNanos = Math.min(parallelToolTimeoutNanos, agentRemainingNanos);
        boolean limitedByAgentDeadline = agentRemainingNanos <= parallelToolTimeoutNanos;
        long batchStartedNanos = System.nanoTime();
        CompletionService<IndexedToolResult> completionService = new ExecutorCompletionService<>(toolExecutor);
        List<Future<IndexedToolResult>> futures = new ArrayList<>(calls.size());

        try {
            int nextCallIndex = 0;
            int initialCalls = Math.min(MAX_IN_FLIGHT_TOOL_CALLS, calls.size());
            while (nextCallIndex < initialCalls) {
                submitTool(completionService, futures, providerName, toolRound, calls, nextCallIndex++);
            }

            ToolResult[] results = new ToolResult[calls.size()];
            for (int completed = 0; completed < calls.size(); completed++) {
                long remainingNanos = timeoutNanos - (System.nanoTime() - batchStartedNanos);
                if (remainingNanos <= 0) {
                    cancel(futures);
                    throw toolBatchTimeout(
                            providerName, parallelToolTimeout, agentTimeout, limitedByAgentDeadline);
                }

                Future<IndexedToolResult> future = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (future == null) {
                    cancel(futures);
                    throw toolBatchTimeout(
                            providerName, parallelToolTimeout, agentTimeout, limitedByAgentDeadline);
                }

                IndexedToolResult result = future.get();
                results[result.index()] = result.result();
                if (nextCallIndex < calls.size()) {
                    submitTool(completionService, futures, providerName, toolRound, calls, nextCallIndex++);
                }
            }
            List<ToolResult> orderedResults = new ArrayList<>(results.length);
            for (ToolResult result : results) {
                orderedResults.add(result);
            }
            return List.copyOf(orderedResults);
        } catch (RejectedExecutionException exception) {
            cancel(futures);
            throw new LlmBusyException(providerName + " tool execution capacity is full", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancel(futures);
            throw new LlmException(providerName + " tool execution was interrupted", exception);
        } catch (ExecutionException exception) {
            cancel(futures);
            Throwable cause = exception.getCause();
            if (cause instanceof LlmException llmException) {
                throw llmException;
            }
            throw new LlmException(providerName + " parallel tool execution failed", cause);
        } catch (CancellationException exception) {
            cancel(futures);
            throw new LlmException(providerName + " parallel tool execution was cancelled", exception);
        }
    }

    private void submitTool(
            CompletionService<IndexedToolResult> completionService,
            List<Future<IndexedToolResult>> futures,
            String providerName,
            int toolRound,
            List<ResolvedToolCall> calls,
            int callIndex
    ) {
        ResolvedToolCall call = calls.get(callIndex);
        futures.add(completionService.submit(() -> new IndexedToolResult(
                callIndex, executeResolvedTool(providerName, toolRound, call))));
    }

    private LlmException toolBatchTimeout(
            String providerName,
            Duration parallelToolTimeout,
            Duration agentTimeout,
            boolean limitedByAgentDeadline
    ) {
        return limitedByAgentDeadline
                ? agentTimeout(providerName, agentTimeout, null)
                : parallelTimeout(providerName, parallelToolTimeout);
    }

    private LlmException parallelTimeout(String providerName, Duration timeout) {
        return new LlmException(providerName + " parallel tool execution timed out after " + timeout);
    }

    private LlmException agentTimeout(String providerName, Duration timeout, Throwable cause) {
        String message = providerName + " agent execution timed out after " + timeout;
        return cause == null ? new LlmException(message) : new LlmException(message, cause);
    }

    private long requireAgentActive(
            String providerName,
            Duration agentTimeout,
            long agentTimeoutNanos,
            long agentStartedNanos
    ) {
        if (Thread.currentThread().isInterrupted()) {
            throw new LlmException(providerName + " agent execution was interrupted");
        }
        long remainingNanos = agentTimeoutNanos - (System.nanoTime() - agentStartedNanos);
        if (remainingNanos <= 0) {
            throw agentTimeout(providerName, agentTimeout, null);
        }
        return remainingNanos;
    }

    private long requirePositiveDuration(String providerName, String setting, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(providerName + " " + setting + " must be positive");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(providerName + " " + setting + " is too large", exception);
        }
    }

    private ToolResult executeResolvedTool(
            String providerName,
            int toolRound,
            ResolvedToolCall call
    ) {
        log.info("Executing LLM tool provider={}, name={}, callId={}, toolRound={}",
                providerName, call.tool().name(), call.toolCall().id(), toolRound);
        return new ToolResult(call.toolCall(), executeTool(providerName, call.tool(), call.toolCall().arguments()));
    }

    private void cancel(List<? extends Future<?>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    private Map<String, LlmTool<?>> validateTools(List<LlmTool<?>> tools) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM tool is required");
        }
        Map<String, LlmTool<?>> toolsByName = new HashMap<>();
        for (LlmTool<?> tool : tools) {
            if (tool == null) {
                throw new IllegalArgumentException("LLM tools cannot contain null");
            }
            if (toolsByName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate LLM tool name: " + tool.name());
            }
        }
        return toolsByName;
    }

    private String executeTool(String providerName, LlmTool<?> tool, String arguments) {
        try {
            Object output = tool.execute(arguments, objectMapper);
            return output instanceof String text ? text : objectMapper.writeValueAsString(output);
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmException(providerName + " tool execution failed: " + tool.name(), exception);
        }
    }

    public interface AgentLoop<R> {

        R callModel();

        List<LlmToolCall> findToolCalls(R response);

        void preserveModelResponse(R response);

        void appendToolResults(List<ToolResult> results);
    }

    private record ResolvedToolCall(LlmToolCall toolCall, LlmTool<?> tool) {
    }

    private record IndexedToolResult(int index, ToolResult result) {
    }

    public record ToolResult(LlmToolCall toolCall, String output) {
    }
}
