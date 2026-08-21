package com.windrunner.server.llmproviders.openai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm.openai")
public class OpenAIProperties {

    private String model = "gpt-5.6-luna";

    private String apiKey;

    private String baseUrl = "https://api.openai.com/v1";

    private int maxOutputTokens = 2048;

    private String reasoningEffort = "low";

    private int maxToolRounds = 8;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofMinutes(2);
}
