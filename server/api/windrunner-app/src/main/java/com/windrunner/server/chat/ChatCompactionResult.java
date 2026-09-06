package com.windrunner.server.chat;

import com.windrunner.server.llm.LlmMessage;

import java.util.List;

public record ChatCompactionResult(List<LlmMessage> messages, String summary) {
}
