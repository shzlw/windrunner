package com.windrunner.server.tools;

import com.windrunner.server.llm.LlmTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

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
    public List<LlmTool<?>> llmTools() {
        List<LlmTool<?>> functions = new ArrayList<>();
        for (Tool<?> tool : toolsByName.values()) {
            functions.add(toLlmTool(tool));
        }
        return List.copyOf(functions);
    }

    private <T> LlmTool<T> toLlmTool(Tool<T> tool) {
        return new LlmTool<>(
                tool.name(),
                tool.description(),
                tool.parametersType(),
                tool::execute
        );
    }
}
