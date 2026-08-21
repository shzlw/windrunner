package com.windrunner.server.project;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.user.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;

    public ProjectAccessService(ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository,
                                ProjectTeamRepository projectTeamRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectTeamRepository = projectTeamRepository;
    }

    public void requireProjectRole(String projectId, AppUser user, String minimumRole) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            return;
        }
        if (!hasProjectRole(projectId, user.getId(), minimumRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access is required");
        }
    }

    public boolean hasProjectRole(String projectId, String userId, String minimumRole) {
        List<String> roles = rolesAtOrAbove(minimumRole);
        return projectMemberRepository.hasDirectRole(projectId, userId, roles)
                || projectMemberRepository.hasTeamRole(projectId, userId, roles);
    }

    public int countOwners(String projectId) {
        return projectMemberRepository.countOwners(projectId) + projectTeamRepository.countOwners(projectId);
    }

    public void requireAnotherOwnerBeforeRemovingOwner(String projectId, boolean removingOwner) {
        if (removingOwner && countOwners(projectId) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one project owner is required");
        }
    }

    private List<String> rolesAtOrAbove(String minimumRole) {
        String normalized = ProjectRoles.normalize(minimumRole);
        if (ProjectRoles.OWNER.equals(normalized)) {
            return List.of(ProjectRoles.OWNER);
        }
        if (ProjectRoles.EDITOR.equals(normalized)) {
            return List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR);
        }
        return List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR, ProjectRoles.VIEWER);
    }
}
