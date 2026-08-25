package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.apikey.ApiKeyService;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.external.auth.ExternalApiKeyAuthService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class McpAuthenticationFilterTest {

    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final McpAuthenticationFilter filter = new McpAuthenticationFilter(
            new ExternalApiKeyAuthService(apiKeyService));

    @AfterEach
    void clearRequestContext() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requiresProjectsReadApiKey() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(apiKeyService.authenticate("token")).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid API key"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void storesAuthenticatedOwnerForToolCall() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AppUser owner = new AppUser();
        owner.setId("user-1");
        when(apiKeyService.authenticate("token")).thenReturn(new AuthenticatedApiKey(null, owner,
                List.of(ApiKeyScopes.PROJECTS_READ)));

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(McpAuthenticationFilter.ACTOR_REQUEST_ATTRIBUTE)).isSameAs(owner);
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticatesKeysWithAnyScopesSinceToolsEnforceTheirOwnScopes() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AppUser owner = new AppUser();
        owner.setId("user-1");
        // Empty scope list: the filter only proves key validity; tools check scopes.
        when(apiKeyService.authenticate("token")).thenReturn(new AuthenticatedApiKey(null, owner, List.of()));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(McpAuthenticationFilter.ACTOR_REQUEST_ATTRIBUTE)).isSameAs(owner);
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest mcpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/mcp");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
