package com.windrunner.server.llm;

public record LlmUsageContext(
        String userId,
        String projectId,
        com.windrunner.server.llm.domain.LlmUsageFeature feature
) {
}