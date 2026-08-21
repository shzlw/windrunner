package com.windrunner.server.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmAvailabilityService {

    private static final String NONE_PROVIDER = "none";

    private final LlmProperties properties;
    private final ObjectProvider<LlmService> llmServiceProvider;

    public String provider() {
        String provider = properties.getProvider();
        return provider == null || provider.isBlank() ? NONE_PROVIDER : provider.trim().toLowerCase();
    }

    public boolean available() {
        return !NONE_PROVIDER.equals(provider()) && llmServiceProvider.getIfAvailable() != null;
    }
}
