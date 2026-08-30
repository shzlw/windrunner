package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectMcpToolsTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final McpAuthorization authorization = mock(McpAuthorization.class);

    @Test
    void listsProjectsVisibleToApiKeyOwner() {
        AppUser owner = new AppUser();
        owner.setId("user-1");
        owner.setGlobalRole("USER");
        when(authorization.requireScope(ApiKeyScopes.PROJECTS_READ)).thenReturn(owner);

        Project project = new Project();
        project.setId("proj-1");
        project.setName("Windrunner");
        when(projectRepository.findVisibleToUserPaged("user-1", 50, 0)).thenReturn(List.of(project));
        when(projectRepository.countVisibleToUser("user-1")).thenReturn(1L);

        ProjectMcpTools.ProjectPage result = new ProjectMcpTools(projectRepository, authorization).listProjects(null, null);

        assertThat(result.projects()).containsExactly(new ProjectMcpTools.ProjectSummary("proj-1", "Windrunner"));
        assertThat(result.total()).isOne();
        assertThat(result.hasMore()).isFalse();
        verify(projectRepository).findVisibleToUserPaged("user-1", 50, 0);
        verify(projectRepository).countVisibleToUser("user-1");
        verify(projectRepository, never()).findVisibleToUser("user-1");
        verify(projectRepository, never()).findAllByOrderByNameAscIdAsc();
        verify(authorization).requireScope(ApiKeyScopes.PROJECTS_READ);
    }
}
