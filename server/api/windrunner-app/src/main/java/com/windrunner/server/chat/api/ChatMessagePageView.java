package com.windrunner.server.chat.api;

import com.windrunner.server.chat.domain.ChatMessage;

import java.util.List;

public record ChatMessagePageView(
        List<ChatMessage> items,
        boolean hasEarlier,
        String beforeCursor,
        int limit
) {
}
