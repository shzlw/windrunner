package com.windrunner.server.auth.security;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.domain.AuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsrfFilterTest {

    @Mock
    private AuthService authService;

    @Test
    void bearerRequestsSkipCsrfEvenWhenSessionCookieIsPresent() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.setRequestURI("/api/v1/projects");
        request.addHeader("Authorization", "Bearer api-key");
        MockFilterChain chain = new MockFilterChain();

        new CsrfFilter(authService).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void bearerRequestsToInternalApiStillRequireCsrf() {
        AuthSession session = new AuthSession();
        session.setCsrfToken("session-token");
        when(authService.resolveAuthSession(any())).thenReturn(Optional.of(session));

        MockHttpServletRequest request = request("POST");
        request.setRequestURI("/internal-api/v1/projects");
        request.addHeader("Authorization", "Bearer api-key");

        assertThatThrownBy(() -> new CsrfFilter(authService).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo("Invalid CSRF token"));
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        return request;
    }
}
