package com.windrunner.server.llmproviders.compatible;

import java.time.Duration;

public record OpenAICompatibleSettings(
        String providerName,
        String model,
        int maxOutputTokens,
        String reasoningEffort,
        int maxToolRounds,
        boolean parallelToolCalls,
        Duration parallelToolTimeout
) {
}
