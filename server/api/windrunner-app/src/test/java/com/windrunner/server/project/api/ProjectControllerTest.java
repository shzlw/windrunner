package com.windrunner.server.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectContentDeletionService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectTeamRepository projectTeamRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuthService authService;
    @Mock
    private ProjectContentDeletionService projectContentDeletionService;
    @Mock
    private HttpServletRequest request;

    @Test
    void createProjectRequiresAtLeastOneOwner() {
        ProjectController controller = controller();
        when(authService.requireCurrentUser(request)).thenReturn(actor());

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.createProject(new CreateProjectRequest("Atlas", List.of(), List.of()), request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("At least one project owner is required");
        verifyNoInteractions(projectRepository, projectMemberRepository, projectTeamRepository);
    }

    @Test
    void createProjectAssignsUserAndTeamOwners() {
        ProjectController controller = controller();
        when(authService.requireCurrentUser(request)).thenReturn(actor());
        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(appUserRepository.findById("user-2")).thenReturn(Optional.of(user("user-2")));
        when(teamRepository.existsById("team-1")).thenReturn(true);

        Project project = controller.createProject(
                new CreateProjectRequest(" Atlas ", List.of("user-1", "user-2", "user-1"), List.of("team-1")),
                request).data();

        ArgumentCaptor<String> projectId = ArgumentCaptor.forClass(String.class);
        verify(projectRepository).insert(projectId.capture(), anyString(), anyString());
        assertThat(project.getId()).isEqualTo(projectId.getValue());
        assertThat(project.getId()).startsWith("proj_");
        assertThat(project.getName()).isEqualTo("Atlas");
        assertThat(project.getCreatedByUserId()).isEqualTo("actor-1");
        verify(projectMemberRepository).upsert(projectId.getValue(), "user-1", ProjectRoles.OWNER);
        verify(projectMemberRepository).upsert(projectId.getValue(), "user-2", ProjectRoles.OWNER);
        verify(projectTeamRepository).upsert(projectId.getValue(), "team-1", ProjectRoles.OWNER);
    }

    private ProjectController controller() {
        return new ProjectController(
                projectRepository,
                projectMemberRepository,
                projectTeamRepository,
                teamRepository,
                appUserRepository,
                projectAccessService,
                auditLogService,
                authService,
                new EntityIdGenerator(),
                projectContentDeletionService);
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        return actor;
    }

    private AppUser user(String id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setGlobalRole("USER");
        return user;
    }
}
