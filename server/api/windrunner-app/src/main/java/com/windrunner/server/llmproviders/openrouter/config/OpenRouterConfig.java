package com.windrunner.server.llmproviders.openrouter.config;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llmproviders.openrouter.OpenRouterService;
import org.springframework.beans.factory.annotation.Qualifier;
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
@EnableConfigurationProperties(OpenRouterProperties.class)
@ConditionalOnProperty(prefix = "windrunner.llm", name = "provider", havingValue = "openrouter")
public class OpenRouterConfig {

    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties properties) {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing OpenRouter API key: configure windrunner.llm.openrouter.api-key");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("Missing OpenRouter base URL: configure windrunner.llm.openrouter.base-url");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("Missing OpenRouter model: configure windrunner.llm.openrouter.model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory);
        if (StringUtils.hasText(properties.getSiteUrl())) {
            builder.defaultHeader("HTTP-Referer", properties.getSiteUrl());
        }
        if (StringUtils.hasText(properties.getSiteName())) {
            builder.defaultHeader("X-OpenRouter-Title", properties.getSiteName());
        }
        return builder.build();
    }

    @Bean
    public LlmService openRouterService(
            @Qualifier("openRouterRestClient") RestClient openRouterRestClient,
            OpenRouterProperties openRouterProperties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        return new OpenRouterService(openRouterRestClient, openRouterProperties, objectMapper, agentService);
    }
}
