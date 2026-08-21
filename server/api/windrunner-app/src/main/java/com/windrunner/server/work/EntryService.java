package com.windrunner.server.work;

import com.windrunner.server.audit.AuditActions;
import com.windrunner.server.audit.AuditEntityTypes;
import com.windrunner.server.audit.AuditLogEntry;
import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.audit.AuditOutcomes;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class EntryService {
    private final EntryRepository entries; private final WorkItemService workItems; private final RelationshipRepository relationships; private final ContentOrderService contentOrder; private final EntityIdGenerator ids; private final AuditLogService auditLogService; private final AppUserRepository users; private final com.windrunner.server.search.SearchNormalizer searchNormalizer; private final com.windrunner.server.notification.NotificationService notificationService; private final com.windrunner.server.notification.WorkItemNotificationAudience notificationAudience;
    public List<Entry> list(String projectId) { return populateAuthorDisplayNames(entries.findByProjectId(projectId)); }
    public Entry get(String projectId, String id) { return populateAuthorDisplayName(entries.findById(id).filter(e -> projectId.equals(e.getProjectId())).orElseThrow(() -> WorkItemService.notFound("Entry not found"))); }
    @Transactional public Entry create(String projectId, Entry entry, String actorId) { return createWithId(projectId, ids.generate(EntityIdType.ENTRY), entry, actorId); }
    @Transactional public Entry createWithId(String projectId, String id, Entry entry, String actorId) { entry.setProjectId(projectId); entry.setAuthorUserId(actorId); normalize(entry); workItems.get(projectId, entry.getWorkItemId()); entry.setSortIndex(contentOrder.nextSortIndex(projectId, entry.getWorkItemId())); entry.setId(id); entries.insert(entry.getId(), projectId, entry.getWorkItemId(), entry.getSortIndex(), actorId, entry.getType(), entry.getBody(), searchNormalizer.normalize(entry.getBody())); Entry created = get(projectId, entry.getId()); auditLogService.logAfterCommit(audit(actorId, AuditActions.CREATE, created, null, snapshot(created))); notifyEntryCreated(created, actorId); return created; }
    @Transactional public Entry update(String projectId, String id, Entry entry, String actorId) { Entry current = get(projectId, id); Map<String, Object> before = snapshot(current); normalize(entry); if (entries.update(id, projectId, entry.getType(), entry.getBody(), searchNormalizer.normalize(entry.getBody())) == 0) throw WorkItemService.notFound("Entry not found"); Entry updated = get(projectId, id); auditLogService.logAfterCommit(audit(actorId, AuditActions.UPDATE, updated, before, snapshot(updated))); return updated; }
    @Transactional public void delete(String projectId, String id, String actorId) {
        Entry current = get(projectId, id); Map<String, Object> before = snapshot(current);
        List<Relationship> projectRelationships = relationships.findByProjectId(projectId);
        Set<String> affectedQuestionIds = projectRelationships.stream()
                .filter(relationship -> "ACCEPTED_ANSWER".equals(relationship.getType()))
                .filter(relationship -> "ENTRY".equals(relationship.getToEntityType()) && id.equals(relationship.getToEntityId()))
                .map(Relationship::getFromEntityId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> questionsWithAnotherAcceptedAnswer = projectRelationships.stream()
                .filter(relationship -> "ACCEPTED_ANSWER".equals(relationship.getType()))
                .filter(relationship -> affectedQuestionIds.contains(relationship.getFromEntityId()))
                .filter(relationship -> !id.equals(relationship.getToEntityId()))
                .map(Relationship::getFromEntityId)
                .collect(java.util.stream.Collectors.toSet());
        relationships.deleteForEntity(projectId, "ENTRY", id); entries.deleteInProject(id, projectId);
        affectedQuestionIds.stream()
                .filter(questionId -> !questionsWithAnotherAcceptedAnswer.contains(questionId))
                .forEach(questionId -> {
                    var question = workItems.get(projectId, questionId);
                    if ("ANSWERED".equals(question.getStatus())) {
                        question.setStatus("OPEN");
                        workItems.update(projectId, questionId, question, null, actorId);
                    }
                });
        auditLogService.logAfterCommit(audit(actorId, AuditActions.DELETE, current, before, null));
    }
    private void notifyEntryCreated(Entry created, String actorId) {
        try {
            WorkItem item = workItems.get(created.getProjectId(), created.getWorkItemId());
            notificationService.notifyWorkItemActivity(
                    notificationAudience.resolve(created.getWorkItemId(), actorId),
                    actorId,
                    created.getProjectId(),
                    created.getWorkItemId(),
                    item.getTitle(),
                    List.of("added an entry"));
        } catch (RuntimeException ignored) {
            // Notification failures must never fail entry creation.
        }
    }

    private void normalize(Entry entry) { if (entry == null || WorkItemService.blank(entry.getWorkItemId()) || WorkItemService.blank(entry.getBody())) throw WorkItemService.bad("Entry workItemId and body are required"); entry.setType(WorkItemService.enumValue(entry.getType() == null ? "COMMENT" : entry.getType(), WorkTypes.ENTRY_TYPES, "Entry type")); entry.setBody(entry.getBody().trim()); }
    private List<Entry> populateAuthorDisplayNames(List<Entry> results) { Map<String, AppUser> usersById = new LinkedHashMap<>(); users.findAllById(results.stream().map(Entry::getAuthorUserId).filter(java.util.Objects::nonNull).distinct().toList()).forEach(user -> usersById.put(user.getId(), user)); results.forEach(entry -> entry.setAuthorDisplayName(displayName(usersById.get(entry.getAuthorUserId())))); return results; }
    private Entry populateAuthorDisplayName(Entry entry) { entry.setAuthorDisplayName(users.findById(entry.getAuthorUserId()).map(this::displayName).orElse(null)); return entry; }
    private String displayName(AppUser user) { if (user == null) return null; if (!WorkItemService.blank(user.getDisplayName())) return user.getDisplayName().trim(); if (!WorkItemService.blank(user.getUsername())) return user.getUsername(); return user.getEmail(); }
    private Map<String, Object> snapshot(Entry entry) { Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("id", entry.getId()); snapshot.put("workItemId", entry.getWorkItemId()); snapshot.put("sortIndex", entry.getSortIndex()); snapshot.put("authorUserId", entry.getAuthorUserId()); snapshot.put("type", entry.getType()); snapshot.put("body", entry.getBody()); return snapshot; }
    private AuditLogEntry audit(String actorId, String action, Entry entry, Map<String, Object> before, Map<String, Object> after) { return new AuditLogEntry(actorId, action, AuditEntityTypes.ENTRY, entry.getId(), entry.getProjectId(), AuditOutcomes.SUCCESS, action + " entry", auditLogService.json(before), auditLogService.json(after), auditLogService.changes(before, after), null); }
}
