package com.windrunner.server.audio.config;

import com.windrunner.server.audio.AudioTranscriptionProvider;
import com.windrunner.server.audio.OpenAITranscriptionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(
        prefix = "windrunner.audio.transcription",
        name = "provider",
        havingValue = "openai",
        matchIfMissing = true
)
public class OpenAITranscriptionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "windrunner.audio.transcription", name = "enabled", havingValue = "true")
    public RestClient openAITranscriptionRestClient(AudioTranscriptionProperties properties) {
        AudioTranscriptionProperties.OpenAIProperties openAI = properties.getOpenai();
        if (!StringUtils.hasText(openAI.getApiKey())) {
            throw new IllegalStateException(
                    "Missing OpenAI transcription API key: configure OPENAI_TRANSCRIPTION_API_KEY or OPENAI_API_KEY");
        }
        if (!StringUtils.hasText(openAI.getBaseUrl())) {
            throw new IllegalStateException("Missing OpenAI transcription base URL");
        }
        if (!StringUtils.hasText(openAI.getModel())) {
            throw new IllegalStateException("Missing OpenAI transcription model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(openAI.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAI.getApiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "windrunner.audio.transcription", name = "enabled", havingValue = "true")
    public AudioTranscriptionProvider openAITranscriptionProvider(
            @Qualifier("openAITranscriptionRestClient") RestClient restClient,
            AudioTranscriptionProperties properties
    ) {
        return new OpenAITranscriptionProvider(restClient, properties.getOpenai());
    }
}
