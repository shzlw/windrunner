package com.windrunner.server.llmproviders.ollama.config;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llmproviders.ollama.OllamaService;
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
@EnableConfigurationProperties(OllamaProperties.class)
@ConditionalOnProperty(prefix = "windrunner.llm", name = "provider", havingValue = "ollama")
public class OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties properties) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("Missing Ollama base URL: configure windrunner.llm.ollama.base-url");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("Missing Ollama model: configure windrunner.llm.ollama.model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory);
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
        return builder.build();
    }

    @Bean
    public LlmService ollamaService(
            @Qualifier("ollamaRestClient") RestClient ollamaRestClient,
            OllamaProperties ollamaProperties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        return new OllamaService(ollamaRestClient, ollamaProperties, objectMapper, agentService);
    }
}
