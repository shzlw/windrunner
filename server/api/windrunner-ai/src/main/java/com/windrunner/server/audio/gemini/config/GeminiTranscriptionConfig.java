package com.windrunner.server.audio.gemini.config;

import com.windrunner.server.audio.AudioTranscriptionProvider;
import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import com.windrunner.server.audio.gemini.GeminiTranscriptionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "windrunner.audio.transcription", name = "provider", havingValue = "gemini")
public class GeminiTranscriptionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "windrunner.audio.transcription", name = "enabled", havingValue = "true")
    public RestClient geminiTranscriptionRestClient(
            AudioTranscriptionProperties properties,
            GeminiTranscriptionProperties gemini
    ) {
        if (!StringUtils.hasText(gemini.getApiKey())) {
            throw new IllegalStateException(
                    "Missing Gemini transcription API key: configure GEMINI_TRANSCRIPTION_API_KEY or GEMINI_API_KEY");
        }
        if (!StringUtils.hasText(gemini.getBaseUrl())) {
            throw new IllegalStateException("Missing Gemini transcription base URL");
        }
        if (!StringUtils.hasText(gemini.getModel())) {
            throw new IllegalStateException("Missing Gemini transcription model");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(gemini.getBaseUrl())
                .defaultHeader("x-goog-api-key", gemini.getApiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "windrunner.audio.transcription", name = "enabled", havingValue = "true")
    public AudioTranscriptionProvider geminiTranscriptionProvider(
            @Qualifier("geminiTranscriptionRestClient") RestClient restClient,
            GeminiTranscriptionProperties properties
    ) {
        return new GeminiTranscriptionProvider(restClient, properties);
    }
}
