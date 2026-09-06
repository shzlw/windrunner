package com.windrunner.server.chat;

import com.windrunner.server.chat.domain.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageCursorUtilsTest {

    @Test
    void roundTripsMessageOrderingValues() {
        ChatMessage message = new ChatMessage();
        message.setId("message-42");
        message.setCreatedAt(OffsetDateTime.of(2026, 9, 6, 12, 30, 45, 123_000_000, ZoneOffset.ofHours(-5)));

        ChatMessageCursorUtils.Cursor cursor = ChatMessageCursorUtils.parse(ChatMessageCursorUtils.encode(message));

        assertThat(cursor.id()).isEqualTo("message-42");
        assertThat(cursor.createdAt()).isEqualTo(message.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC));
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> ChatMessageCursorUtils.parse("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message cursor is invalid");
    }
}
