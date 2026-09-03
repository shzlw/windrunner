package com.windrunner.server.audio;

import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
public class AudioTranscriptionService {

    private final AudioTranscriptionProvider provider;
    private final AudioTranscriptionProperties properties;

    public String transcribe(MultipartFile file, String language) {
        return provider.transcribe(AudioTranscriptionRequest.from(file, language));
    }

    public String provider() {
        return provider.id();
    }

    public String model() {
        return provider.model();
    }

    public int maxDurationSeconds() {
        return properties.getMaxDurationSeconds();
    }

    public long maxFileSizeBytes() {
        return properties.getMaxFileSizeBytes();
    }
}
