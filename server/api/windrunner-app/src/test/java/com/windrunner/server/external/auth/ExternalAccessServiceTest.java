package com.windrunner.server.external.auth;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalAccessServiceTest {

    @Mock
    private ExternalApiKeyAuthService apiKeyAuthService;
    @Mock
    private HttpServletRequest request;

    @Test
    void requireScopesChecksAllScopesWithOneAuthentication() {
        AppUser owner = new AppUser();
        when(apiKeyAuthService.authenticate(request)).thenReturn(new AuthenticatedApiKey(
                null, owner, List.of(ApiKeyScopes.PROJECTS_READ, ApiKeyScopes.WORK_ITEMS_READ)));

        assertThat(new ExternalAccessService(apiKeyAuthService).requireScopes(
                request, ApiKeyScopes.PROJECTS_READ, ApiKeyScopes.WORK_ITEMS_READ)).isSameAs(owner);

        verify(apiKeyAuthService).authenticate(request);
    }

    @Test
    void requireScopesRejectsMissingScope() {
        when(apiKeyAuthService.authenticate(request)).thenReturn(new AuthenticatedApiKey(
                null, new AppUser(), List.of(ApiKeyScopes.PROJECTS_READ)));

        assertThatThrownBy(() -> new ExternalAccessService(apiKeyAuthService).requireScopes(
                request, ApiKeyScopes.PROJECTS_READ, ApiKeyScopes.WORK_ITEMS_READ))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
