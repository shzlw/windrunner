package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.WorkTypes;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.RelationshipDraft;
import com.windrunner.server.work.domain.Relationship;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
final class RelationshipProposalHandler implements ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> {
    private final RelationshipService relationshipService;
    private final ProjectAccessService projectAccessService;

    @Override
    public String getEntityType() {
        return "RELATIONSHIP";
    }

    @Override
    public void authorize(WorkspaceProposalChange change, AppUser actor) {
        projectAccessService.requireProjectRole(change.projectId(), actor, ProjectRoles.EDITOR);
    }

    @Override
    public WorkspacePreparedChange prepare(WorkspaceProposalChange change, AppUser actor) {
        RelationshipDraft draft = change.draft() == null ? null : change.draft().relationship();
        Relationship previous = null;
        Relationship relationship;

        if ("ADD".equals(change.action())) {
            if (draft == null) throw createBadRequestException("New relationships require proposed values");
            relationship = new Relationship();
            relationship.setId(change.targetId());
            relationship.setProjectId(change.projectId());
            relationship.setFromEntityType(normalizeEntityType(draft.fromEntityType(), "From entity type"));
            relationship.setFromEntityId(resolveRef(draft.fromEntityId(), change.reservedIds()));
            relationship.setToEntityType(normalizeEntityType(draft.toEntityType(), "To entity type"));
            relationship.setToEntityId(resolveRef(draft.toEntityId(), change.reservedIds()));
            relationship.setType(normalizeRelationshipType(draft.type()));
            relationship.setReason(convertBlankToNull(draft.reason()));
            relationship.setSourceEntryId(resolveNullableRef(draft.sourceEntryId(), change.reservedIds()));
        } else {
            previous = requireRelationship(change.projectId(), change.targetId());
            relationship = copyRelationship(previous);
            if ("UPDATE".equals(change.action()) && draft != null && draft.reason() != null) {
                relationship.setReason(convertBlankToNull(draft.reason()));
            }
        }

        return new WorkspacePreparedChange(
                JsonUtils.toJson(relationship),
                previous == null ? null : JsonUtils.toJson(previous),
                previous == null ? JsonUtils.toJson(Map.of()) : JsonUtils.toJson(previous));
    }

    @Override
    public void apply(WorkspaceProposalChange change, WorkspacePreparedChange prepared, AppUser actor) {
        Relationship payload = JsonUtils.fromJson(prepared.payloadJson(), Relationship.class);
        if ("ADD".equals(change.action())) {
            relationshipService.createWithId(change.projectId(), change.targetId(), payload, actor.getId());
            return;
        }

        Relationship before = JsonUtils.fromJson(prepared.previousJson(), Relationship.class);
        Relationship current = requireRelationship(change.projectId(), change.targetId());
        if (!Objects.equals(current, before)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Relationship changed after this AI suggestion was created. Ask AI to review it again.");
        }
        if ("UPDATE".equals(change.action())) {
            relationshipService.updateReason(change.projectId(), change.targetId(), payload.getReason(), actor.getId());
        } else {
            relationshipService.delete(change.projectId(), change.targetId(), actor.getId());
        }
    }

    @Override
    public WorkspaceProposalChange buildRevert(ProposalChange appliedChange, AppUser actor) {
        WorkspaceProposalTarget target = JsonUtils.fromJson(appliedChange.getTargetRef(), WorkspaceProposalTarget.class);
        return switch (appliedChange.getOperation()) {
            case "ADD" -> buildDeleteRevert(appliedChange, target);
            case "UPDATE" -> buildUpdateRevert(appliedChange, target);
            case "DELETE" -> buildAddRevert(appliedChange, target);
            default -> throw createBadRequestException("This relationship proposal cannot be reverted");
        };
    }

    private WorkspaceProposalChange buildDeleteRevert(ProposalChange appliedChange, WorkspaceProposalTarget target) {
        Relationship expected = JsonUtils.fromJson(appliedChange.getAfterSnapshot(), Relationship.class);
        Relationship current = requireRelationship(target.projectId(), target.targetId());
        requireSameRelationship(current, expected,
                "Cannot revert because the relationship changed after the proposal was applied");
        return createRevertChange(target, "DELETE", null);
    }

    private WorkspaceProposalChange buildUpdateRevert(ProposalChange appliedChange, WorkspaceProposalTarget target) {
        Relationship before = JsonUtils.fromJson(appliedChange.getBeforeSnapshot(), Relationship.class);
        Relationship after = JsonUtils.fromJson(appliedChange.getAfterSnapshot(), Relationship.class);
        if (Objects.equals(before.getReason(), after.getReason())) {
            throw createBadRequestException("The original relationship change has nothing to revert");
        }
        Relationship current = requireRelationship(target.projectId(), target.targetId());
        requireSameRelationship(current, after,
                "Cannot revert because the relationship changed after the proposal was applied");
        RelationshipDraft draft = new RelationshipDraft(null, null, null, null, null,
                before.getReason() == null ? "" : before.getReason(), null);
        return createRevertChange(target, "UPDATE", draft);
    }

    private WorkspaceProposalChange buildAddRevert(ProposalChange appliedChange, WorkspaceProposalTarget target) {
        Relationship before = JsonUtils.fromJson(appliedChange.getBeforeSnapshot(), Relationship.class);
        List<Relationship> currentRelationships = relationshipService.list(target.projectId());
        if (currentRelationships.stream().anyMatch(relationship -> target.targetId().equals(relationship.getId()))) {
            throw createConflictException("Cannot revert because the deleted relationship ID is already in use");
        }
        if (currentRelationships.stream().anyMatch(relationship -> hasSameEndpointsAndType(relationship, before))) {
            throw createConflictException("Cannot revert because an equivalent relationship already exists");
        }
        if ("ACCEPTED_ANSWER".equals(before.getType()) && currentRelationships.stream().anyMatch(relationship ->
                "ACCEPTED_ANSWER".equals(relationship.getType())
                        && Objects.equals(relationship.getFromEntityType(), before.getFromEntityType())
                        && Objects.equals(relationship.getFromEntityId(), before.getFromEntityId()))) {
            throw createConflictException("Cannot revert because the question now has another accepted answer");
        }
        RelationshipDraft draft = new RelationshipDraft(
                before.getFromEntityType(), before.getFromEntityId(), before.getToEntityType(), before.getToEntityId(),
                before.getType(), before.getReason(), before.getSourceEntryId());
        return createRevertChange(target, "ADD", draft);
    }

    private WorkspaceProposalChange createRevertChange(WorkspaceProposalTarget target, String action,
                                                        RelationshipDraft relationshipDraft) {
        ChangeDraft draft = new ChangeDraft("RELATIONSHIP", action, target.targetId(), null,
                "Revert " + target.summary(), null, null, relationshipDraft);
        return new WorkspaceProposalChange(target.projectId(), action, target.targetId(), draft.summary(), draft, Map.of());
    }

    private void requireSameRelationship(Relationship current, Relationship expected, String message) {
        if (!hasSameEndpointsAndType(current, expected)
                || !Objects.equals(current.getReason(), expected.getReason())
                || !Objects.equals(current.getSourceEntryId(), expected.getSourceEntryId())) {
            throw createConflictException(message);
        }
    }

    private boolean hasSameEndpointsAndType(Relationship left, Relationship right) {
        return Objects.equals(left.getFromEntityType(), right.getFromEntityType())
                && Objects.equals(left.getFromEntityId(), right.getFromEntityId())
                && Objects.equals(left.getToEntityType(), right.getToEntityType())
                && Objects.equals(left.getToEntityId(), right.getToEntityId())
                && Objects.equals(left.getType(), right.getType());
    }

    private Relationship requireRelationship(String projectId, String relationshipId) {
        return relationshipService.list(projectId).stream()
                .filter(candidate -> relationshipId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relationship not found"));
    }

    private Relationship copyRelationship(Relationship source) {
        Relationship copy = new Relationship();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFromEntityType(source.getFromEntityType());
        copy.setFromEntityId(source.getFromEntityId());
        copy.setToEntityType(source.getToEntityType());
        copy.setToEntityId(source.getToEntityId());
        copy.setType(source.getType());
        copy.setReason(source.getReason());
        copy.setSourceEntryId(source.getSourceEntryId());
        copy.setCreatedByUserId(source.getCreatedByUserId());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private String normalizeEntityType(String value, String label) {
        String normalized = value == null ? null : value.trim().toUpperCase();
        if (normalized == null || !WorkTypes.ENTITY_TYPES.contains(normalized)) {
            throw createBadRequestException(label + " is invalid");
        }
        return normalized;
    }

    private String normalizeRelationshipType(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase();
        if (normalized == null || !WorkTypes.RELATIONSHIP_TYPES.contains(normalized)) {
            throw createBadRequestException("Relationship type is invalid");
        }
        return normalized;
    }

    private String resolveRef(String value, Map<String, String> reservedIds) {
        if (value == null || value.isBlank()) throw createBadRequestException("Entity reference is required");
        String normalized = value.trim();
        return reservedIds.getOrDefault(normalized, normalized);
    }

    private String resolveNullableRef(String value, Map<String, String> reservedIds) {
        if (value == null || value.isBlank() || "PROJECT_ROOT".equalsIgnoreCase(value.trim())) return null;
        return resolveRef(value, reservedIds);
    }

    private String convertBlankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException createBadRequestException(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException createConflictException(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
