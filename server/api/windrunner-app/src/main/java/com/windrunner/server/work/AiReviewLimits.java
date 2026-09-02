package com.windrunner.server.work;

public final class AiReviewLimits {
    public static final int MAX_RELATED_ENTRIES = 20;
    public static final int MAX_RELATED_RELATIONSHIPS = 50;
    public static final int MAX_CHILDREN = 50;
    public static final int MAX_SEARCH_RESULTS = 20;
    public static final int MAX_TITLE_LENGTH = 240;
    public static final int MAX_TEXT_LENGTH = 1200;
    public static final int MAX_INSTRUCTION_LENGTH = 4000;

    private AiReviewLimits() {
    }

    public static String bounded(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "…";
    }
}
