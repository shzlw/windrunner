package com.windrunner.server.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared password rules for every path that sets a password: admin user
 * creation, admin resets, and self-service changes.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 1_000;

    private PasswordPolicy() {
    }

    public static void assertValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_LENGTH + " characters");
        }
        assertMaxLength(password);
    }

    public static void assertMaxLength(String password) {
        if (password != null && password.length() > MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at most " + MAX_LENGTH + " characters");
        }
    }
}
