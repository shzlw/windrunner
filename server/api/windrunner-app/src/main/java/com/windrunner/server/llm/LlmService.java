package com.windrunner.server.llm;

import java.util.List;

public interface LlmService {

    LlmResult<String> runChatWithTools(
            List<LlmMessage> messages,
            String instructions,
            List<LlmTool<?>> tools
    );
}
