package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.apikey.domain.ApiKey;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpAuthorizationTest {

    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUpRequestContext() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsTheAuthenticatedApiKeyAfterScopeAndProjectChecks() {
        AppUser actor = createActor("user-1");
        AuthenticatedApiKey apiKey = createApiKey("key-1", actor);
        when(externalAccessService.requireScopes(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor);
        when(request.getAttribute(McpAuthenticationFilter.API_KEY_REQUEST_ATTRIBUTE)).thenReturn(apiKey);

        AuthenticatedApiKey result = authorization().requireProjectEditorApiKey(
                " project-1 ", ApiKeyScopes.WORK_ITEMS_WRITE);

        assertThat(result).isSameAs(apiKey);
        verify(projectAccessService).requireProjectRole("project-1", actor, ProjectRoles.EDITOR);
    }

    @Test
    void rejectsAnApiKeyWhoseOwnerDoesNotMatchTheScopeCheck() {
        AppUser actor = createActor("user-1");
        AuthenticatedApiKey apiKey = createApiKey("key-1", createActor("user-2"));
        when(externalAccessService.requireScopes(request, ApiKeyScopes.PROJECTS_READ)).thenReturn(actor);
        when(request.getAttribute(McpAuthenticationFilter.API_KEY_REQUEST_ATTRIBUTE)).thenReturn(apiKey);

        assertThatThrownBy(() -> authorization().requireProjectViewerApiKey(
                "project-1", ApiKeyScopes.PROJECTS_READ))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(projectAccessService, never()).requireProjectRole("project-1", actor, ProjectRoles.VIEWER);
    }

    private McpAuthorization authorization() {
        return new McpAuthorization(externalAccessService, projectAccessService, projectRepository);
    }

    private AppUser createActor(String id) {
        AppUser actor = new AppUser();
        actor.setId(id);
        return actor;
    }

    private AuthenticatedApiKey createApiKey(String id, AppUser owner) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(id);
        return new AuthenticatedApiKey(apiKey, owner, List.of());
    }
}
