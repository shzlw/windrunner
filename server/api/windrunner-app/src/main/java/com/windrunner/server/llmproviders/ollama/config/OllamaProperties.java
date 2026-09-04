package com.windrunner.server.llmproviders.ollama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm.ollama")
public class OllamaProperties {

    private String model;

    private String apiKey;

    private String baseUrl = "http://localhost:11434/v1";

    private int maxOutputTokens = 2048;

    private String reasoningEffort;

    private int maxToolRounds = 8;

    private boolean parallelToolCalls = false;

    private Duration parallelToolTimeout = Duration.ofSeconds(30);

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);
}
