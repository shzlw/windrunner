package com.windrunner.server.chat;

import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSessionSummary;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionSummaryRepository;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llm.LlmUsageContext;
import com.windrunner.server.llm.LlmUsageService;
import com.windrunner.server.llm.domain.LlmUsageFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatCompactionServiceTest {

    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatSessionSummaryRepository summaryRepository;
    @Mock
    private ChatService chatService;
    @Mock
    private LlmUsageService llmUsageService;
    @Mock
    private LlmService llmService;

    @InjectMocks
    private ChatCompactionService compactionService;

    @Test
    void compactsOldMessagesBeforeReturningTheModelContext() {
        List<ChatMessage> messages = createMessages(40, 2_500);
        List<ChatMessage> newestFirst = new ArrayList<>(messages);
        Collections.reverse(newestFirst);
        when(summaryRepository.findByChatSessionId("session-1")).thenReturn(Optional.empty());
        when(messageRepository.findLatestPage("session-1", 49)).thenReturn(newestFirst);
        when(llmService.runChatWithTools(anyList(), anyString(), anyList()))
                .thenReturn(new LlmResult<>("response-1", "test-model", "compact summary", 1L, 1L, 2L));

        ChatCompactionResult result = compactionService.prepare(
                "session-1",
                "new message",
                llmService,
                new LlmUsageContext("user-1", null, LlmUsageFeature.CHAT));

        assertThat(result.summary()).isEqualTo("compact summary");
        assertThat(result.messages()).hasSize(13);
        assertThat(result.messages().getLast()).isEqualTo(new LlmMessage("user", "new message"));
        verify(chatService).saveChatSessionSummary("session-1", null, "compact summary", messages.get(27));
    }

    @Test
    void reusesTheExistingSummaryWhenTheRemainingContextFits() {
        List<ChatMessage> messages = createMessages(20, 100);
        List<ChatMessage> newestFirst = new ArrayList<>(messages);
        Collections.reverse(newestFirst);
        ChatSessionSummary summary = new ChatSessionSummary();
        summary.setSummary("existing memory");
        summary.setSummarizedThroughMessageId(messages.get(7).getId());
        summary.setSummarizedThroughCreatedAt(messages.get(7).getCreatedAt());
        when(summaryRepository.findByChatSessionId("session-1")).thenReturn(Optional.of(summary));
        when(messageRepository.findLatestPage("session-1", 49)).thenReturn(newestFirst);

        ChatCompactionResult result = compactionService.prepare(
                "session-1",
                "new message",
                llmService,
                new LlmUsageContext("user-1", null, LlmUsageFeature.CHAT));

        assertThat(result.summary()).isEqualTo("existing memory");
        List<String> expectedContents = new ArrayList<>(messages.subList(8, 20).stream().map(ChatMessage::getContent).toList());
        expectedContents.add("new message");
        assertThat(result.messages()).extracting(LlmMessage::content)
                .containsExactlyElementsOf(expectedContents);
        verifyNoInteractions(chatService, llmService);
    }

    private List<ChatMessage> createMessages(int count, int contentLength) {
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        List<ChatMessage> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ChatMessage message = new ChatMessage();
            message.setId("message-" + index);
            message.setRole(index % 2 == 0 ? "user" : "assistant");
            message.setContent("x".repeat(contentLength));
            message.setCreatedAt(start.plusSeconds(index));
            messages.add(message);
        }
        return messages;
    }
}
