package com.windrunner.server.llmproviders.claude.config;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llmproviders.claude.ClaudeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
@ConditionalOnProperty(prefix = "windrunner.llm", name = "provider", havingValue = "claude")
public class ClaudeConfig {

    @Bean
    public RestClient claudeRestClient(ClaudeProperties properties) {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing Claude API key: configure windrunner.llm.claude.api-key");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("Missing Claude base URL: configure windrunner.llm.claude.base-url");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("Missing Claude model: configure windrunner.llm.claude.model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", properties.getAnthropicVersion())
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public LlmService claudeService(
            @Qualifier("claudeRestClient") RestClient claudeRestClient,
            ClaudeProperties claudeProperties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        return new ClaudeService(claudeRestClient, claudeProperties, objectMapper, agentService);
    }
}
