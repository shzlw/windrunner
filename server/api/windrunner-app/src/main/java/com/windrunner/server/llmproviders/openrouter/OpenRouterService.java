package com.windrunner.server.llmproviders.openrouter;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.llmproviders.openrouter.config.OpenRouterProperties;
import com.windrunner.server.llmproviders.compatible.OpenAICompatibleLlmService;
import com.windrunner.server.llmproviders.compatible.OpenAICompatibleSettings;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class OpenRouterService implements LlmService {

    private final OpenAICompatibleLlmService delegate;

    public OpenRouterService(
            RestClient restClient,
            OpenRouterProperties properties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.delegate = new OpenAICompatibleLlmService(
                restClient,
                new OpenAICompatibleSettings(
                        "OpenRouter",
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
