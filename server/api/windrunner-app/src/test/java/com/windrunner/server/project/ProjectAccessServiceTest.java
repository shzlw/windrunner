package com.windrunner.server.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.user.domain.AppUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectTeamRepository projectTeamRepository;

    @Test
    void superadminCanAccessExistingProjectWithoutMembership() {
        ProjectAccessService service = service();
        when(projectRepository.existsById("project-1")).thenReturn(true);

        service.requireProjectRole("project-1", user("user-1", AppRoles.SUPERADMIN), ProjectRoles.OWNER);

        verify(projectMemberRepository, never()).hasDirectRole(anyString(), anyString(), anyList());
        verify(projectMemberRepository, never()).hasTeamRole(anyString(), anyString(), anyList());
    }

    @Test
    void directEditorCanPassEditorRequirement() {
        ProjectAccessService service = service();
        when(projectRepository.existsById("project-1")).thenReturn(true);
        when(projectMemberRepository.hasDirectRole("project-1", "user-1", List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR)))
                .thenReturn(true);

        service.requireProjectRole("project-1", user("user-1", AppRoles.USER), ProjectRoles.EDITOR);

        verify(projectMemberRepository).hasDirectRole("project-1", "user-1", List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR));
    }

    @Test
    void teamOwnerCanPassEditorRequirement() {
        ProjectAccessService service = service();
        when(projectRepository.existsById("project-1")).thenReturn(true);
        when(projectMemberRepository.hasTeamRole("project-1", "user-1", List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR)))
                .thenReturn(true);

        service.requireProjectRole("project-1", user("user-1", AppRoles.USER), ProjectRoles.EDITOR);

        verify(projectMemberRepository).hasTeamRole("project-1", "user-1", List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR));
    }

    @Test
    void memberWithoutRequiredRoleIsForbidden() {
        ProjectAccessService service = service();
        when(projectRepository.existsById("project-1")).thenReturn(true);

        assertThatThrownBy(() -> service.requireProjectRole("project-1", user("user-1", AppRoles.USER), ProjectRoles.VIEWER))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getReason()).isEqualTo("Project access is required");
                });
    }

    @Test
    void removingLastOwnerIsBlocked() {
        ProjectAccessService service = service();
        when(projectMemberRepository.countOwners("project-1")).thenReturn(1);
        when(projectTeamRepository.countOwners("project-1")).thenReturn(0);

        assertThatThrownBy(() -> service.requireAnotherOwnerBeforeRemovingOwner("project-1", true))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("At least one project owner is required");
                });
    }

    private ProjectAccessService service() {
        return new ProjectAccessService(projectRepository, projectMemberRepository, projectTeamRepository);
    }

    private AppUser user(String id, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setGlobalRole(role);
        return user;
    }
}
