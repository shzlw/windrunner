package com.windrunner.server.llmproviders.groq.config;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llmproviders.groq.GroqService;
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
@EnableConfigurationProperties(GroqProperties.class)
@ConditionalOnProperty(prefix = "windrunner.llm", name = "provider", havingValue = "groq")
public class GroqConfig {

    @Bean
    public RestClient groqRestClient(GroqProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("Missing Groq API key: configure windrunner.llm.groq.api-key");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("Missing Groq base URL: configure windrunner.llm.groq.base-url");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("Missing Groq model: configure windrunner.llm.groq.model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public LlmService groqService(
            @Qualifier("groqRestClient") RestClient groqRestClient,
            GroqProperties groqProperties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        return new GroqService(groqRestClient, groqProperties, objectMapper, agentService);
    }
}
