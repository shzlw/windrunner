package com.windrunner.server.llm.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.llm.LlmUsageService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmUsageControllerTest {

    @Mock
    private LlmUsageService llmUsageService;
    @Mock
    private AuthService authService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private HttpServletRequest request;

    @Test
    void normalUsersCannotReadLlmUsage() {
        ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        when(authService.requireAdmin(request)).thenThrow(forbidden);

        assertThatThrownBy(() -> controller().summarize(null, null, request))
                .isSameAs(forbidden);

        verify(llmUsageService, never()).summarize(any(), any());
        verify(projectRepository, never()).findVisibleToUser(any());
    }

    @Test
    void adminsCanReadAllVisibleProjectUsage() {
        AppUser admin = new AppUser();
        admin.setId("admin-1");
        Project project = new Project();
        project.setId("project-1");
        LlmUsageSummary summary = new LlmUsageSummary(
                new LlmUsageSummary.Totals(0, 0, 0, 0, 0, 0), List.of(), List.of(), List.of());
        when(authService.requireAdmin(request)).thenReturn(admin);
        when(projectRepository.findVisibleToUser("admin-1")).thenReturn(List.of(project));
        when(llmUsageService.summarize(eq(List.of("project-1")), any())).thenReturn(summary);

        ApiResponse<LlmUsageSummary> response = controller().summarize(null, 30, request);

        assertThat(response.data()).isSameAs(summary);
        verify(authService).requireAdmin(request);
        verify(projectRepository).findVisibleToUser("admin-1");
        verify(llmUsageService).summarize(eq(List.of("project-1")), any());
    }

    private LlmUsageController controller() {
        return new LlmUsageController(llmUsageService, authService, projectAccessService, projectRepository);
    }
}
