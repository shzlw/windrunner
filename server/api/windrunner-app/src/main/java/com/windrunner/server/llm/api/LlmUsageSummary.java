package com.windrunner.server.llm.api;

import java.util.List;

public record LlmUsageSummary(
        Totals totals,
        List<Project> byProject,
        List<Feature> byFeature,
        List<Provider> byProviderModel
) {

    public record Totals(long inputTokens, long outputTokens, long requests, long failures, double successRate,
                         long avgDurationMs) {
    }

    public record Project(String projectId, long inputTokens, long outputTokens, long requests, long failures,
                          double successRate, long avgDurationMs) {
    }

    public record Feature(String feature, long inputTokens, long outputTokens, long requests, long failures,
                          double successRate) {
    }

    public record Provider(String provider, String model, long inputTokens, long outputTokens, long requests,
                           long failures, double successRate) {
    }
}