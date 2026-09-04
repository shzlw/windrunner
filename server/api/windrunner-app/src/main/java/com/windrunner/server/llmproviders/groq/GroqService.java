package com.windrunner.server.llmproviders.groq;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.llmproviders.compatible.OpenAICompatibleLlmService;
import com.windrunner.server.llmproviders.compatible.OpenAICompatibleSettings;
import com.windrunner.server.llmproviders.groq.config.GroqProperties;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class GroqService implements LlmService {

    private final OpenAICompatibleLlmService delegate;

    public GroqService(
            RestClient restClient,
            GroqProperties properties,
            ObjectMapper objectMapper,
            AgentService agentService
    ) {
        this.delegate = new OpenAICompatibleLlmService(
                restClient,
                new OpenAICompatibleSettings(
                        "Groq",
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
