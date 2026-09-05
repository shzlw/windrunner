package com.windrunner.server.llmproviders.ollama;

import com.windrunner.server.llm.*;
import com.windrunner.server.llmproviders.compatible.OpenAICompatibleLlmService;
import com.windrunner.server.llmproviders.compatible.OpenAICompatibleSettings;
import com.windrunner.server.llmproviders.ollama.config.OllamaProperties;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class OllamaService implements LlmService {

    private final OpenAICompatibleLlmService delegate;

    public OllamaService(
            RestClient restClient,
            OllamaProperties properties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.delegate = new OpenAICompatibleLlmService(
                restClient,
                new OpenAICompatibleSettings(
                        "Ollama",
                        properties.getModel(),
                        properties.getMaxOutputTokens(),
                        properties.getReasoningEffort(),
                        properties.getMaxToolRounds(),
                        properties.isParallelToolCalls(),
                        properties.getParallelToolTimeout()),
                objectMapper,
                agentService);
    }

    @Override
    public LlmResult<String> runChatWithTools(
            List<LlmMessage> messages,
            String instructions,
            List<LlmTool<?>> functions
    ) {
        return delegate.runChatWithTools(messages, instructions, functions);
    }
}
