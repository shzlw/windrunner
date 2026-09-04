package com.windrunner.server.llmproviders.gemini.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm.gemini")
public class GeminiProperties {

    private String model = "gemini-3.1-flash-lite";

    private String apiKey;

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private int maxOutputTokens = 2048;

    private float temperature = 1.0f;

    private int maxToolRounds = 8;

    private Duration parallelToolTimeout = Duration.ofSeconds(30);

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);
}
