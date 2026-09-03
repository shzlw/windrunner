package com.windrunner.server.audio.config;

import com.windrunner.server.audio.AudioTranscriptionProvider;
import com.windrunner.server.audio.AudioTranscriptionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AudioTranscriptionProperties.class)
public class AudioTranscriptionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "windrunner.audio.transcription", name = "enabled", havingValue = "true")
    public AudioTranscriptionService audioTranscriptionService(
            ObjectProvider<AudioTranscriptionProvider> providerProvider,
            AudioTranscriptionProperties properties
    ) {
        AudioTranscriptionProvider provider = providerProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException(
                    "Unsupported audio transcription provider: " + properties.getProvider());
        }
        return new AudioTranscriptionService(provider, properties);
    }
}
