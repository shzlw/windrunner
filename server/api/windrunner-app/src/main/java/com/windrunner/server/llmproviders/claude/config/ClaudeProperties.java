package com.windrunner.server.llmproviders.claude.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm.claude")
public class ClaudeProperties {

    private String model = "claude-sonnet-5";

    private String apiKey;

    private String baseUrl = "https://api.anthropic.com/v1";

    private String anthropicVersion = "2023-06-01";

    private int maxOutputTokens = 2048;

    private float temperature = 1.0f;

    private int maxToolRounds = 8;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);
}
