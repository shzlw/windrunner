package com.windrunner.server.external.v1.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.api.CreateProjectRequest;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExternalProjectControllerTest {

    private static final String ACTOR_ID = "user-key-owner";

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
    private ExternalAccessService externalAccessService;
    @Mock
    private HttpServletRequest request;

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId(ACTOR_ID);
        return actor;
    }

    private Project persistedProject() {
        Project project = new Project();
        project.setId("proj-1");
        project.setName("Integration target");
        project.setCreatedByUserId("someone-else");
        project.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        project.setUpdatedAt(OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        return project;
    }

    @Test
    void createProjectAlwaysAddsKeyOwnerAsOwner() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.PROJECTS_WRITE)).thenReturn(actor());
        when(appUserRepository.existsById(anyString())).thenReturn(true);
        ApiResponse<Project> response = controller().createProject(
                new CreateProjectRequest("Integration target", List.of("user-other"), List.of()),
                request);

        // Fix 1: the key owner retains access to the project it created.
        org.mockito.ArgumentCaptor<String> ownerIds = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(projectMemberRepository, org.mockito.Mockito.times(2))
                .upsert(org.mockito.ArgumentMatchers.anyString(), ownerIds.capture(), eq(ProjectRoles.OWNER));
        assertThat(ownerIds.getAllValues()).containsExactly(ACTOR_ID, "user-other");
        verify(appUserRepository).existsById("user-other");
    }

    @Test
    void createProjectRejectsRequestWithoutOwners() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.PROJECTS_WRITE)).thenReturn(actor());

        assertThatThrownBy(() -> controller().createProject(
                new CreateProjectRequest("No owners", List.of(), List.of()), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(projectRepository, never()).insert(anyString(), anyString(), anyString());
    }

    @Test
    void updateProjectReturnsFreshlyLoadedEntityNotRequestObject() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.PROJECTS_WRITE)).thenReturn(actor());
        Project current = persistedProject();
        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(current));
        Project requested = new Project();
        requested.setId("proj-1");
        requested.setName("Renamed by API"); // no createdAt/createdByUserId supplied
        when(projectRepository.update("proj-1", "Renamed by API")).thenReturn(1);
        Project reloaded = persistedProject();
        reloaded.setName("Renamed by API");
        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(current), Optional.of(reloaded));

        ApiResponse<Project> response = controller().updateProject("proj-1", requested, request);

        // Fix 2: the response is the reloaded row with server-owned fields intact.
        assertThat(response.data()).isSameAs(reloaded);
        assertThat(response.data().getName()).isEqualTo("Renamed by API");
        assertThat(response.data().getCreatedAt()).isEqualTo(reloaded.getCreatedAt());
        assertThat(response.data().getCreatedByUserId()).isEqualTo("someone-else");
    }

    private ExternalProjectController controller() {
        return new ExternalProjectController(
                projectRepository,
                projectMemberRepository,
                projectTeamRepository,
                teamRepository,
                appUserRepository,
                projectAccessService,
                auditLogService,
                externalAccessService,
                new EntityIdGenerator());
    }
}
