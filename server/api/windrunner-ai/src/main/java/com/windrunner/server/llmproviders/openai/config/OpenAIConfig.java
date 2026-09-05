package com.windrunner.server.llmproviders.openai.config;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llmproviders.openai.OpenAIService;
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
@EnableConfigurationProperties(OpenAIProperties.class)
@ConditionalOnProperty(prefix = "windrunner.llm", name = "provider", havingValue = "openai")
public class OpenAIConfig {

    @Bean
    public RestClient openAIRestClient(OpenAIProperties openAIProperties) {
        String apiKey = openAIProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing OpenAI API key: configure windrunner.llm.openai.api-key");
        }
        if (!StringUtils.hasText(openAIProperties.getBaseUrl())) {
            throw new IllegalStateException("Missing OpenAI base URL: configure windrunner.llm.openai.base-url");
        }
        if (!StringUtils.hasText(openAIProperties.getModel())) {
            throw new IllegalStateException("Missing OpenAI model: configure windrunner.llm.openai.model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(openAIProperties.getConnectTimeout());
        requestFactory.setReadTimeout(openAIProperties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(openAIProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public LlmService openAIService(
            @Qualifier("openAIRestClient") RestClient openAIRestClient,
            OpenAIProperties openAIProperties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        return new OpenAIService(openAIRestClient, openAIProperties, objectMapper, agentService);
    }
}
