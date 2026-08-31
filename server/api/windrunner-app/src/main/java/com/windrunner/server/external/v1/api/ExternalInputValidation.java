package com.windrunner.server.external.v1.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

final class ExternalInputValidation {
    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_ID_LENGTH = 200;
    static final int MAX_TITLE_LENGTH = 500;
    static final int MAX_DESCRIPTION_LENGTH = 4_000;
    static final int MAX_CONTENT_LENGTH = 20_000;
    static final int MAX_SEARCH_LENGTH = 500;
    static final int MAX_ID_LIST_SIZE = 100;
    static final int MAX_CONTENT_ORDER_SIZE = 10_000;

    private ExternalInputValidation() {
    }

    static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        String trimmed = value.trim();
        requireMaxLength(trimmed, field, maxLength);
        return trimmed;
    }

    static void requireMaxLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be at most " + maxLength + " characters");
        }
    }

    static void requireMaxSize(List<?> values, String field, int maxSize) {
        if (values != null && values.size() > maxSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must contain at most " + maxSize + " items");
        }
    }
}
