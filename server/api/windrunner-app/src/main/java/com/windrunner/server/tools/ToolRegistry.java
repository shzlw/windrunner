package com.windrunner.server.tools;

import com.windrunner.server.llm.LlmTool;

import java.util.List;

public interface ToolRegistry {

    List<LlmTool<?>> llmTools(ToolExecutionContext context);
}
