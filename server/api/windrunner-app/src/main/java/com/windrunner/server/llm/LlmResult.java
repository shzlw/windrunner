package com.windrunner.server.llm;

public record LlmResult<T>(
        String providerResponseId,
        String model,
        T output,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
) {
}
