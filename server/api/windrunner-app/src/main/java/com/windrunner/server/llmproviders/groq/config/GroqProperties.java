package com.windrunner.server.llmproviders.groq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm.groq")
public class GroqProperties {

    private String model = "openai/gpt-oss-20b";

    private String apiKey;

    private String baseUrl = "https://api.groq.com/openai/v1";

    private int maxOutputTokens = 2048;

    private String reasoningEffort;

    private int maxToolRounds = 8;

    private boolean parallelToolCalls = true;

    private Duration parallelToolTimeout = Duration.ofSeconds(30);

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);
}
