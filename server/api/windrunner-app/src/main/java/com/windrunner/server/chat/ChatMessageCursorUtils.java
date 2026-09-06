package com.windrunner.server.chat;

import com.windrunner.server.chat.domain.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

public final class ChatMessageCursorUtils {

    private ChatMessageCursorUtils() {
    }

    public static String encode(ChatMessage message) {
        String value = message.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC) + "\n" + message.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Message cursor is required");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException("Message cursor is invalid");
            }
            return new Cursor(OffsetDateTime.parse(parts[0]), parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Message cursor is invalid", exception);
        }
    }

    public record Cursor(OffsetDateTime createdAt, String id) {
    }
}
