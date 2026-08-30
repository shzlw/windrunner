package com.windrunner.server.chat.api;

import com.windrunner.server.llm.LlmException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageControllerTest {

    @Test
    void doesNotExposeProviderOrToolDetailsInChatErrors() {
        String message = ChatMessageController.userFacingMessage(
                new LlmException("OpenAI tool execution failed: fetch_project_summary"));

        assertThat(message).isEqualTo("The AI couldn't complete your request. Please try again.");
        assertThat(message).doesNotContain("OpenAI", "fetch_project_summary");
    }
}
