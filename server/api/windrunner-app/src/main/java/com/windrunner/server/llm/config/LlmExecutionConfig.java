package com.windrunner.server.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class LlmExecutionConfig {

    private static final int MAX_PARALLEL_AGENT_TASKS = 4;
    private static final int MAX_QUEUED_AGENT_TASKS = 12;
    private static final int MAX_PARALLEL_TOOL_TASKS = 4;
    private static final int MAX_QUEUED_TOOL_TASKS = 16;

    @Bean(name = "llmAgentExecutor", destroyMethod = "close")
    @DependsOn("llmToolExecutor")
    public ExecutorService llmAgentExecutor() {
        return boundedExecutor(MAX_PARALLEL_AGENT_TASKS, MAX_QUEUED_AGENT_TASKS, "llm-agent-");
    }

    @Bean(name = "llmToolExecutor", destroyMethod = "close")
    public ExecutorService llmToolExecutor() {
        return boundedExecutor(MAX_PARALLEL_TOOL_TASKS, MAX_QUEUED_TOOL_TASKS, "llm-tool-");
    }

    private ExecutorService boundedExecutor(int threads, int queueCapacity, String threadNamePrefix) {
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name(threadNamePrefix, 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
