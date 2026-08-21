package com.windrunner.server.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ObjectMapper objectMapper;

    public <R> R run(
            String providerName,
            List<LlmTool<?>> tools,
            int maxToolRounds,
            AgentLoop<R> loop
    ) {
        if (maxToolRounds < 1) {
            throw new IllegalArgumentException(providerName + " max tool rounds must be at least 1");
        }
        Map<String, LlmTool<?>> toolsByName = validateTools(tools);

        int toolRounds = 0;
        while (true) {
            R response = loop.callModel();
            List<LlmToolCall> toolCalls = loop.findToolCalls(response);

            if (toolCalls == null || toolCalls.isEmpty()) {
                return response;
            }

            toolRounds++;
            if (toolRounds > maxToolRounds) {
                throw new LlmException(providerName + " exceeded the maximum tool rounds: " + maxToolRounds);
            }

            loop.preserveModelResponse(response);
            for (LlmToolCall toolCall : toolCalls) {
                if (toolCall == null) {
                    throw new LlmException(providerName + " returned an empty tool call");
                }
                LlmTool<?> tool = toolsByName.get(toolCall.name());
                if (tool == null) {
                    throw new LlmException(providerName + " requested an unregistered tool: " + toolCall.name());
                }

                log.info("Executing LLM tool provider={}, name={}, callId={}, toolRound={}",
                        providerName, tool.name(), toolCall.id(), toolRounds);
                loop.appendToolResult(toolCall, executeTool(providerName, tool, toolCall.arguments()));
            }
        }
    }

    private Map<String, LlmTool<?>> validateTools(List<LlmTool<?>> tools) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM tool is required");
        }
        Map<String, LlmTool<?>> toolsByName = new HashMap<>();
        for (LlmTool<?> tool : tools) {
            if (tool == null) {
                throw new IllegalArgumentException("LLM tools cannot contain null");
            }
            if (toolsByName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate LLM tool name: " + tool.name());
            }
        }
        return toolsByName;
    }

    private String executeTool(String providerName, LlmTool<?> tool, String arguments) {
        try {
            Object output = tool.execute(arguments, objectMapper);
            return output instanceof String text ? text : objectMapper.writeValueAsString(output);
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmException(providerName + " tool execution failed: " + tool.name(), exception);
        }
    }

    public interface AgentLoop<R> {

        R callModel();

        List<LlmToolCall> findToolCalls(R response);

        void preserveModelResponse(R response);

        void appendToolResult(LlmToolCall toolCall, String output);
    }
}
