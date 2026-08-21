package com.windrunner.server.llmproviders.gemini.config;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llmproviders.gemini.GeminiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
@ConditionalOnProperty(prefix = "windrunner.llm", name = "provider", havingValue = "gemini")
public class GeminiConfig {

    @Bean
    public RestClient geminiRestClient(GeminiProperties geminiProperties) {
        String apiKey = geminiProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing Gemini API key: configure windrunner.llm.gemini.api-key");
        }
        if (!StringUtils.hasText(geminiProperties.getBaseUrl())) {
            throw new IllegalStateException("Missing Gemini base URL: configure windrunner.llm.gemini.base-url");
        }
        if (!StringUtils.hasText(geminiProperties.getModel())) {
            throw new IllegalStateException("Missing Gemini model: configure windrunner.llm.gemini.model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(geminiProperties.getConnectTimeout());
        requestFactory.setReadTimeout(geminiProperties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(geminiProperties.getBaseUrl())
                .defaultHeader("X-goog-api-key", apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public LlmService geminiService(
            RestClient geminiRestClient,
            GeminiProperties geminiProperties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        return new GeminiService(geminiRestClient, geminiProperties, objectMapper, agentService);
    }
}
