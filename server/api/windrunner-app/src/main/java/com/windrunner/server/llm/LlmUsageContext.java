package com.windrunner.server.llm;

import com.windrunner.server.llm.domain.LlmUsageFeature;

public record LlmUsageContext(
        String userId,
        String projectId,
        LlmUsageFeature feature
) {
}
