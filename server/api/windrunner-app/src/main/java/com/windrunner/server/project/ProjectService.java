package com.windrunner.server.project;

import com.windrunner.server.audit.AuditActions;
import com.windrunner.server.audit.AuditEntityTypes;
import com.windrunner.server.audit.AuditLogEntry;
import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.audit.AuditOutcomes;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.api.CreateProjectRequest;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamRepository teamRepository;
    private final AppUserRepository appUserRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditLogService auditLogService;
    private final AuthService authService;
    private final EntityIdGenerator idGenerator;
    private final ProjectContentDeletionService projectContentDeletionService;

    @Transactional
    public Project createProject(CreateProjectRequest createRequest, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        if (createRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String name = requireText(createRequest.name(), "Project name is required");
        List<String> ownerUserIds = normalizeIds(createRequest.ownerUserIds(), "Owner user id is required");
        List<String> ownerTeamIds = normalizeIds(createRequest.ownerTeamIds(), "Owner team id is required");
        if (ownerUserIds.isEmpty() && ownerTeamIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one project owner is required");
        }
        for (String ownerUserId : ownerUserIds) {
            requireAssignableProjectMember(ownerUserId);
        }
        for (String ownerTeamId : ownerTeamIds) {
            if (!teamRepository.existsById(ownerTeamId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project owner team not found");
            }
        }

        Project project = new Project();
        project.setId(idGenerator.generate(EntityIdType.PROJECT));
        project.setName(name);
        project.setCreatedByUserId(actor.getId());
        projectRepository.insert(project.getId(), project.getName(), actor.getId());
        if (!AppRoles.isSuperAdmin(actor.getGlobalRole())) {
            projectMemberRepository.upsert(project.getId(), actor.getId(), ProjectRoles.OWNER);
        }
        for (String ownerUserId : ownerUserIds) {
            projectMemberRepository.upsert(project.getId(), ownerUserId, ProjectRoles.OWNER);
        }
        for (String ownerTeamId : ownerTeamIds) {
            projectTeamRepository.upsert(project.getId(), ownerTeamId, ProjectRoles.OWNER);
        }
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.CREATE,
                AuditEntityTypes.PROJECT,
                project.getId(),
                project.getId(),
                AuditOutcomes.SUCCESS,
                "Created project " + project.getName(),
                null,
                auditLogService.toJson(createProjectSnapshot(project)),
                null,
                null));
        return project;
    }

    @Transactional
    public Project updateProject(String id, Project project, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project beforeProject = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        Map<String, Object> before = createProjectSnapshot(beforeProject);
        validateName(project);
        project.setId(id);
        if (projectRepository.update(project.getId(), project.getName()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        Map<String, Object> after = createProjectSnapshot(project);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.PROJECT,
                project.getId(),
                project.getId(),
                AuditOutcomes.SUCCESS,
                "Updated project " + project.getName(),
                auditLogService.toJson(before),
                auditLogService.toJson(after),
                auditLogService.describeChanges(before, after),
                null));
        return project;
    }

    @Transactional
    public void deleteProject(String id, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        Map<String, Object> before = createProjectSnapshot(project);
        projectContentDeletionService.deleteProjectContent(id);
        projectTeamRepository.deleteByProjectId(id);
        projectMemberRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.DELETE,
                AuditEntityTypes.PROJECT,
                project.getId(),
                project.getId(),
                AuditOutcomes.SUCCESS,
                "Deleted project " + project.getName(),
                auditLogService.toJson(before),
                null,
                null,
                null));
    }

    private void validateName(Project project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        project.setName(project.getName().trim());
    }

    private Map<String, Object> createProjectSnapshot(Project project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", project.getId());
        snapshot.put("name", project.getName());
        snapshot.put("createdByUserId", project.getCreatedByUserId());
        snapshot.put("archivedAt", project.getArchivedAt() == null ? null : project.getArchivedAt().toString());
        return snapshot;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private List<String> normalizeIds(List<String> values, String blankMessage) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, blankMessage);
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private void requireAssignableProjectMember(String userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super admin users cannot be added as project members");
        }
    }
}
