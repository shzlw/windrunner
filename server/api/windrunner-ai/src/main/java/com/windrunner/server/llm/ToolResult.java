package com.windrunner.server.llm;

public record ToolResult(LlmToolCall toolCall, String output) {
}
