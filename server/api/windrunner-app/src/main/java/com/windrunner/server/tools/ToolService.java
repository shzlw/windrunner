package com.windrunner.server.tools;

import com.windrunner.server.llm.LlmTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolService implements ToolRegistry {

    private final Map<String, Tool<?>> toolsByName;

    public ToolService(List<Tool<?>> tools) {
        Map<String, Tool<?>> registeredTools = new LinkedHashMap<>();
        for (Tool<?> tool : tools) {
            if (tool == null) {
                throw new IllegalArgumentException("Tools cannot contain null");
            }
            if (registeredTools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate tool name: " + tool.name());
            }
        }
        this.toolsByName = Map.copyOf(registeredTools);
    }

    @Override
    public List<LlmTool<?>> llmTools(ToolExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Tool execution context is required");
        }
        List<LlmTool<?>> functions = new ArrayList<>();
        for (Tool<?> tool : toolsByName.values()) {
            functions.add(toLlmTool(tool, context));
        }
        return List.copyOf(functions);
    }

    private <T> LlmTool<T> toLlmTool(Tool<T> tool, ToolExecutionContext context) {
        return new LlmTool<>(
                tool.name(),
                tool.description(),
                tool.parametersType(),
                parameters -> tool.execute(parameters, context)
        );
    }
}
