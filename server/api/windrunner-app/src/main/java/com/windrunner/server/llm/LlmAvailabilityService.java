package com.windrunner.server.llm;

import com.windrunner.server.llmproviders.claude.config.ClaudeProperties;
import com.windrunner.server.llmproviders.gemini.config.GeminiProperties;
import com.windrunner.server.llmproviders.openai.config.OpenAIProperties;
import com.windrunner.server.llmproviders.openrouter.config.OpenRouterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmAvailabilityService {

    private static final String NONE_PROVIDER = "none";

    private final LlmProperties properties;
    private final ObjectProvider<LlmService> llmServiceProvider;
    private final ObjectProvider<OpenAIProperties> openAIProperties;
    private final ObjectProvider<OpenRouterProperties> openRouterProperties;
    private final ObjectProvider<ClaudeProperties> claudeProperties;
    private final ObjectProvider<GeminiProperties> geminiProperties;

    public String provider() {
        String provider = properties.getProvider();
        return provider == null || provider.isBlank() ? NONE_PROVIDER : provider.trim().toLowerCase();
    }

    public boolean available() {
        return !NONE_PROVIDER.equals(provider()) && llmServiceProvider.getIfAvailable() != null;
    }

    public String model() {
        return switch (provider()) {
            case "openai" -> valueOrUnknown(openAIProperties.getIfAvailable(), OpenAIProperties::getModel);
            case "openrouter" -> valueOrUnknown(openRouterProperties.getIfAvailable(), OpenRouterProperties::getModel);
            case "claude" -> valueOrUnknown(claudeProperties.getIfAvailable(), ClaudeProperties::getModel);
            case "gemini" -> valueOrUnknown(geminiProperties.getIfAvailable(), GeminiProperties::getModel);
            default -> "—";
        };
    }

    private <T> String valueOrUnknown(T properties, java.util.function.Function<T, String> modelReader) {
        String value = properties == null ? null : modelReader.apply(properties);
        return value == null || value.isBlank() ? "—" : value;
    }
}
