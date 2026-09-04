package com.windrunner.server.llmproviders.openrouter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm.openrouter")
public class OpenRouterProperties {

    private String model;

    private String apiKey;

    private String baseUrl = "https://openrouter.ai/api/v1";

    private String siteUrl;

    private String siteName;

    private int maxOutputTokens = 2048;

    private String reasoningEffort;

    private int maxToolRounds = 8;

    private boolean parallelToolCalls = true;

    private Duration parallelToolTimeout = Duration.ofSeconds(30);

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);
}
