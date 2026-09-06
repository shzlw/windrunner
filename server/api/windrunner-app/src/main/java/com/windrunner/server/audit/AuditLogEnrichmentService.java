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

        Map<String, String> userNames = findUserNames(auditLogs);
        Map<String, String> projectNames = findProjectNames(auditLogs);
        Map<String, String> teamNames = findEntityNames(auditLogs, AuditEntityTypes.TEAM,
                teamRepository::findAllById, Team::getId, Team::getName);
        Map<String, String> workItemNames = findEntityNames(auditLogs, AuditEntityTypes.WORK_ITEM,
                workItemRepository::findByIds, WorkItem::getId, WorkItem::getTitle);
        Map<String, String> entryNames = findEntityNames(auditLogs, AuditEntityTypes.ENTRY,
                entryRepository::findAllById, Entry::getId, this::getEntryName);
        Map<String, String> apiKeyNames = findEntityNames(auditLogs, AuditEntityTypes.API_KEY,
                apiKeyRepository::findAllById, ApiKey::getId, ApiKey::getName);

        auditLogs.forEach(auditLog -> {
            auditLog.setActorDisplayName(findNameFor(userNames, auditLog.getActorUserId()));
            auditLog.setProjectName(findNameFor(projectNames, auditLog.getProjectId()));
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

    private Map<String, String> findUserNames(List<AuditLog> auditLogs) {
        List<String> ids = auditLogs.stream()
                .map(AuditLog::getActorUserId)
                .filter(this::hasText)
                .distinct()
                .toList();
        return findByIds(ids, userRepository::findAllById, AppUser::getId, this::getDisplayName);
    }

    private Map<String, String> findProjectNames(List<AuditLog> auditLogs) {
        List<String> projectIds = auditLogs.stream()
                .map(AuditLog::getProjectId)
                .filter(this::hasText)
                .distinct()
                .toList();
        return findByIds(projectIds, projectRepository::findAllById, Project::getId, Project::getName);
    }

    private <T> Map<String, String> findEntityNames(
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
            case AuditEntityTypes.USER -> findNameFor(userNames, id);
            case AuditEntityTypes.PROJECT -> findNameFor(projectNames, id);
            case AuditEntityTypes.TEAM -> findNameFor(teamNames, id);
            case AuditEntityTypes.WORK_ITEM -> findNameFor(workItemNames, id);
            case AuditEntityTypes.ENTRY -> findNameFor(entryNames, id);
            case AuditEntityTypes.API_KEY -> findNameFor(apiKeyNames, id);
            default -> null;
        };
    }

    private String findNameFor(Map<String, String> names, String id) {
        return hasText(id) ? names.get(id) : null;
    }

    private String getEntryName(Entry entry) {
        if (hasText(entry.getBody())) {
            String body = entry.getBody().trim().replaceAll("\\s+", " ");
            return body.length() > 80 ? body.substring(0, 77) + "..." : body;
        }
        return entry.getType();
    }

    private String getDisplayName(AppUser user) {
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
