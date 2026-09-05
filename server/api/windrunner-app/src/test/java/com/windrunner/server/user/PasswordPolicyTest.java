package com.windrunner.server.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordAtMinimumLength() {
        assertThatCode(() -> PasswordPolicy.assertValid("abcdef")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.assertValid("longer password 123")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.assertValid("abcde"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsNullAndEmptyPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.assertValid(null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> PasswordPolicy.assertValid(""))
                .isInstanceOf(ResponseStatusException.class);
    }
}
