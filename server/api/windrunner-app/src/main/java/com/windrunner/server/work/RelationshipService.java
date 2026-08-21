package com.windrunner.server.work;

import com.windrunner.server.audit.AuditActions;
import com.windrunner.server.audit.AuditEntityTypes;
import com.windrunner.server.audit.AuditLogEntry;
import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.audit.AuditOutcomes;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RelationshipService {
    private final RelationshipRepository relationships; private final WorkItemService workItems; private final EntryRepository entries; private final EntityIdGenerator ids; private final AuditLogService auditLogService; private final com.windrunner.server.search.SearchNormalizer searchNormalizer;
    public java.util.List<Relationship> list(String projectId) { return relationships.findByProjectId(projectId); }
    @Transactional public Relationship create(String projectId, Relationship relationship, String actorId) {
        return createWithId(projectId, ids.generate(EntityIdType.RELATIONSHIP), relationship, actorId);
    }
    @Transactional public Relationship createWithId(String projectId, String id, Relationship relationship, String actorId) {
        relationship.setProjectId(projectId); relationship.setCreatedByUserId(actorId); normalize(projectId, relationship);
        if ("ACCEPTED_ANSWER".equals(relationship.getType())) {
            relationships.deleteFromWorkItemByType(projectId, relationship.getFromEntityId(), "ACCEPTED_ANSWER");
        }
        relationship.setId(id);
        relationships.insert(relationship.getId(), projectId, relationship.getFromEntityType(), relationship.getFromEntityId(), relationship.getToEntityType(), relationship.getToEntityId(), relationship.getType(), relationship.getReason(), relationship.getSourceEntryId(), actorId, searchNormalizer.normalize(relationship.getReason()));
        Relationship created = relationships.findById(relationship.getId()).orElseThrow();
        auditLogService.logAfterCommit(audit(actorId, AuditActions.CREATE, created, null, snapshot(created)));
        if ("ACCEPTED_ANSWER".equals(relationship.getType())) {
            WorkItem question = workItems.get(projectId, relationship.getFromEntityId());
            if (!"ANSWERED".equals(question.getStatus())) {
                question.setStatus("ANSWERED");
                workItems.update(projectId, question.getId(), question, null, actorId);
            }
        }
        return created;
    }
    @Transactional public void delete(String projectId, String id, String actorId) {
        Relationship current = relationships.findById(id).filter(r -> projectId.equals(r.getProjectId())).orElseThrow(() -> WorkItemService.notFound("Relationship not found"));
        Map<String, Object> before = snapshot(current);
        relationships.deleteInProject(id, projectId);
        auditLogService.logAfterCommit(audit(actorId, AuditActions.DELETE, current, before, null));
    }
    @Transactional public Relationship updateReason(String projectId, String id, String reason, String actorId) {
        Relationship relationship = relationships.findById(id).filter(r -> projectId.equals(r.getProjectId())).orElseThrow(() -> WorkItemService.notFound("Relationship not found"));
        String normalizedReason = WorkItemService.blank(reason) ? null : reason.trim();
        Map<String, Object> before = snapshot(relationship);
        if (relationships.updateReason(id, projectId, normalizedReason, searchNormalizer.normalize(normalizedReason)) == 0) throw WorkItemService.notFound("Relationship not found");
        relationship.setReason(normalizedReason);
        auditLogService.logAfterCommit(audit(actorId, AuditActions.UPDATE, relationship, before, snapshot(relationship)));
        return relationship;
    }
    private void normalize(String projectId, Relationship r) {
        if (r == null) throw WorkItemService.bad("Relationship is required");
        r.setFromEntityType(WorkItemService.enumValue(r.getFromEntityType(), WorkTypes.ENTITY_TYPES, "From entity type"));
        r.setToEntityType(WorkItemService.enumValue(r.getToEntityType(), WorkTypes.ENTITY_TYPES, "To entity type"));
        r.setType(WorkItemService.enumValue(r.getType(), WorkTypes.RELATIONSHIP_TYPES, "Relationship type"));
        requireEntity(projectId, r.getFromEntityType(), r.getFromEntityId()); requireEntity(projectId, r.getToEntityType(), r.getToEntityId());
        if (r.getFromEntityType().equals(r.getToEntityType()) && r.getFromEntityId().equals(r.getToEntityId())) throw WorkItemService.bad("A relationship cannot point to itself");
        r.setReason(WorkItemService.blank(r.getReason()) ? null : r.getReason().trim());
        if ("ACCEPTED_ANSWER".equals(r.getType())) validateAcceptedAnswer(projectId, r);
        if (r.getSourceEntryId() != null && !r.getSourceEntryId().isBlank()) entries.findById(r.getSourceEntryId()).filter(e -> projectId.equals(e.getProjectId())).orElseThrow(() -> WorkItemService.bad("Source entry must belong to the project"));
    }
    private void validateAcceptedAnswer(String projectId, Relationship relationship) {
        if (!"WORK_ITEM".equals(relationship.getFromEntityType()) || !"ENTRY".equals(relationship.getToEntityType())) {
            throw WorkItemService.bad("An accepted answer must connect a question to an entry");
        }
        WorkItem question = workItems.get(projectId, relationship.getFromEntityId());
        if (!"QUESTION".equals(question.getType())) {
            throw WorkItemService.bad("Only a question can have an accepted answer");
        }
        Entry answer = entries.findById(relationship.getToEntityId())
                .filter(entry -> projectId.equals(entry.getProjectId()))
                .orElseThrow(() -> WorkItemService.bad("Accepted answer entry must belong to the project"));
        if (!question.getId().equals(answer.getWorkItemId())) {
            throw WorkItemService.bad("Accepted answer must belong to the question");
        }
    }
    private void requireEntity(String projectId, String type, String id) { if (WorkItemService.blank(id)) throw WorkItemService.bad("Relationship entity id is required"); if ("WORK_ITEM".equals(type)) workItems.get(projectId, id); else entries.findById(id).filter(e -> projectId.equals(e.getProjectId())).orElseThrow(() -> WorkItemService.bad("Relationship entry must belong to the project")); }
    private Map<String, Object> snapshot(Relationship r) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", r.getType());
        snapshot.put("fromEntityType", r.getFromEntityType());
        snapshot.put("fromEntityId", r.getFromEntityId());
        snapshot.put("toEntityType", r.getToEntityType());
        snapshot.put("toEntityId", r.getToEntityId());
        snapshot.put("reason", r.getReason());
        return snapshot;
    }
    private AuditLogEntry audit(String actorId, String action, Relationship r, Map<String, Object> before, Map<String, Object> after) {
        String summary = action + " relationship " + r.getType() + " (" + r.getFromEntityType() + " " + r.getFromEntityId() + " -> " + r.getToEntityType() + " " + r.getToEntityId() + ")";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("relationshipType", r.getType());
        metadata.put("fromEntityType", r.getFromEntityType());
        metadata.put("fromEntityId", r.getFromEntityId());
        metadata.put("toEntityType", r.getToEntityType());
        metadata.put("toEntityId", r.getToEntityId());
        return new AuditLogEntry(actorId, action, AuditEntityTypes.RELATIONSHIP, r.getId(), r.getProjectId(), AuditOutcomes.SUCCESS, summary,
                auditLogService.json(before), auditLogService.json(after), auditLogService.changes(before, after), auditLogService.json(metadata));
    }
}
