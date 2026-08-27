package com.windrunner.server.audit;

import com.windrunner.server.apikey.domain.ApiKey;
import com.windrunner.server.apikey.persistence.ApiKeyRepository;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RequiredArgsConstructor
@Service
public class AuditLogEnrichmentService {

    private final AppUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final WorkItemRepository workItemRepository;
    private final EntryRepository entryRepository;
    private final ApiKeyRepository apiKeyRepository;

    public void enrich(List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            return;
        }

        Map<String, String> userNames = userNames(auditLogs);
        Map<String, String> projectNames = projectNames(auditLogs);
        Map<String, String> teamNames = entityNames(auditLogs, AuditEntityTypes.TEAM,
                teamRepository::findAllById, Team::getId, Team::getName);
        Map<String, String> workItemNames = entityNames(auditLogs, AuditEntityTypes.WORK_ITEM,
                workItemRepository::findByIds, WorkItem::getId, WorkItem::getTitle);
        Map<String, String> entryNames = entityNames(auditLogs, AuditEntityTypes.ENTRY,
                entryRepository::findAllById, Entry::getId, this::entryName);
        Map<String, String> apiKeyNames = entityNames(auditLogs, AuditEntityTypes.API_KEY,
                apiKeyRepository::findAllById, ApiKey::getId, ApiKey::getName);

        auditLogs.forEach(auditLog -> {
            auditLog.setActorDisplayName(userNames.get(auditLog.getActorUserId()));
            auditLog.setProjectName(projectNames.get(auditLog.getProjectId()));
            auditLog.setEntityDisplayName(entityName(
                    auditLog,
                    userNames,
                    projectNames,
                    teamNames,
                    workItemNames,
                    entryNames,
                    apiKeyNames));
        });
    }

    private Map<String, String> userNames(List<AuditLog> auditLogs) {
        List<String> ids = auditLogs.stream()
                .map(AuditLog::getActorUserId)
                .filter(this::hasText)
                .distinct()
                .toList();
        return findByIds(ids, userRepository::findAllById, AppUser::getId, this::displayUser);
    }

    private Map<String, String> projectNames(List<AuditLog> auditLogs) {
        List<String> projectIds = auditLogs.stream()
                .map(AuditLog::getProjectId)
                .filter(this::hasText)
                .distinct()
                .toList();
        return findByIds(projectIds, projectRepository::findAllById, Project::getId, Project::getName);
    }

    private <T> Map<String, String> entityNames(
            List<AuditLog> auditLogs,
            String entityType,
            Function<Collection<String>, Iterable<T>> loader,
            Function<T, String> idExtractor,
            Function<T, String> displayName) {
        List<String> ids = auditLogs.stream()
                .filter(auditLog -> entityType.equals(auditLog.getEntityType()))
                .map(AuditLog::getEntityId)
                .filter(this::hasText)
                .distinct()
                .toList();
        return findByIds(ids, loader, idExtractor, displayName);
    }

    private <T> Map<String, String> findByIds(
            Collection<String> ids,
            Function<Collection<String>, Iterable<T>> loader,
            Function<T, String> idExtractor,
            Function<T, String> displayName) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new LinkedHashMap<>();
        loader.apply(ids).forEach(item -> {
            String name = displayName.apply(item);
            String id = idExtractor.apply(item);
            if (id != null && name != null && !name.isBlank()) {
                names.put(id, name);
            }
        });
        return names;
    }

    private String entityName(
            AuditLog auditLog,
            Map<String, String> userNames,
            Map<String, String> projectNames,
            Map<String, String> teamNames,
            Map<String, String> workItemNames,
            Map<String, String> entryNames,
            Map<String, String> apiKeyNames) {
        String id = auditLog.getEntityId();
        if (AuditEntityTypes.AUTH.equals(auditLog.getEntityType())) {
            return "Authentication";
        }
        if (AuditEntityTypes.RELATIONSHIP.equals(auditLog.getEntityType())) {
            return "Relationship";
        }
        if (AuditEntityTypes.TEAM_JOIN_REQUEST.equals(auditLog.getEntityType())) {
            return "Team join request";
        }
        return switch (auditLog.getEntityType()) {
            case AuditEntityTypes.USER -> userNames.get(id);
            case AuditEntityTypes.PROJECT -> projectNames.get(id);
            case AuditEntityTypes.TEAM -> teamNames.get(id);
            case AuditEntityTypes.WORK_ITEM -> workItemNames.get(id);
            case AuditEntityTypes.ENTRY -> entryNames.get(id);
            case AuditEntityTypes.API_KEY -> apiKeyNames.get(id);
            default -> null;
        };
    }

    private String entryName(Entry entry) {
        if (hasText(entry.getBody())) {
            String body = entry.getBody().trim().replaceAll("\\s+", " ");
            return body.length() > 80 ? body.substring(0, 77) + "..." : body;
        }
        return entry.getType();
    }

    private String displayUser(AppUser user) {
        if (hasText(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        if (hasText(user.getUsername())) {
            return user.getUsername();
        }
        return user.getEmail();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
