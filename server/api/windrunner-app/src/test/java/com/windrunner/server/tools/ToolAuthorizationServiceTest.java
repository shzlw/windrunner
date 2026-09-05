package com.windrunner.server.tools;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolAuthorizationServiceTest {

    @Test
    void revalidatesAccessForAProjectInTheRequestContext() {
        ToolAuthorizationService authorization = authorization();

        assertThatCode(() -> authorization.requireProject(context(), "project-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAProjectOutsideTheRequestContextBeforeLoadingIt() {
        ToolAuthorizationService authorization = authorization();

        assertThatThrownBy(() -> authorization.requireProject(context(), "project-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not in the selected context");
    }

    @Test
    void rejectsMissingToolContext() {
        ToolAuthorizationService authorization = authorization();

        assertThatThrownBy(() -> authorization.requireContext(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Tool execution context is required");
    }

    private ToolAuthorizationService authorization() {
        ProjectRepository projects = proxy(ProjectRepository.class, (method, args) ->
                method.getName().equals("existsById") ? true : unsupported(method));
        ProjectMemberRepository members = proxy(ProjectMemberRepository.class, (method, args) -> {
            if (method.getName().equals("hasDirectRole")) return true;
            return unsupported(method);
        });
        ProjectTeamRepository projectTeams = proxy(ProjectTeamRepository.class,
                (method, args) -> unsupported(method));
        AuthService authService = mock(AuthService.class);
        when(authService.requireActiveActor(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new ToolAuthorizationService(new ProjectAccessService(projects, members, projectTeams), authService);
    }

    private ToolExecutionContext context() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        actor.setGlobalRole("USER");
        return new ToolExecutionContext(actor, "session-1", List.of("project-1"));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return type.getSimpleName() + "Probe";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return handler.invoke(method, args);
                });
    }

    private Object unsupported(java.lang.reflect.Method method) {
        throw new UnsupportedOperationException(method.toString());
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args);
    }
}
