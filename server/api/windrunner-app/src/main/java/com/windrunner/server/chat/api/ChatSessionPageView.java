package com.windrunner.server.chat.api;

import java.util.List;

public record ChatSessionPageView(
        List<ChatSessionSummaryView> items,
        boolean hasMore,
        int offset,
        int limit
) {
}
