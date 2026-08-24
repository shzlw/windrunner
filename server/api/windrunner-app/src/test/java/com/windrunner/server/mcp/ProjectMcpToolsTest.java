package com.windrunner.server.mcp;

import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectMcpToolsTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listsProjectsVisibleToApiKeyOwner() {
        AppUser owner = new AppUser();
        owner.setId("user-1");
        owner.setGlobalRole("USER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(McpAuthenticationFilter.ACTOR_REQUEST_ATTRIBUTE, owner);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Project project = new Project();
        project.setId("proj-1");
        project.setName("Windrunner");
        when(projectRepository.findVisibleToUser("user-1")).thenReturn(List.of(project));

        List<ProjectMcpTools.ProjectSummary> result = new ProjectMcpTools(projectRepository).listProjects();

        assertThat(result).containsExactly(new ProjectMcpTools.ProjectSummary("proj-1", "Windrunner"));
        verify(projectRepository).findVisibleToUser("user-1");
        verify(projectRepository, never()).findAllByOrderByNameAscIdAsc();
    }
}
