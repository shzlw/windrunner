package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.WorkTypes;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.EntryDraft;
import com.windrunner.server.work.domain.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
final class EntryProposalHandler implements ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> {
    private final EntryService entryService;
    private final ProjectAccessService projectAccessService;

    @Override
    public String getEntityType() {
        return "ENTRY";
    }

    @Override
    public void authorize(WorkspaceProposalChange change, AppUser actor) {
        projectAccessService.requireProjectRole(change.projectId(), actor, ProjectRoles.EDITOR);
    }

    @Override
    public WorkspacePreparedChange prepare(WorkspaceProposalChange change, AppUser actor) {
        EntryDraft draft = change.draft() == null ? null : change.draft().entry();
        Entry previous = null;
        Entry entry;

        if ("ADD".equals(change.action())) {
            if (draft == null || isBlank(draft.body()) || isBlank(draft.workItemId())) {
                throw createBadRequestException("New entries require workItemId and body");
            }
            entry = new Entry();
            entry.setId(change.targetId());
            entry.setProjectId(change.projectId());
            entry.setWorkItemId(resolveRef(draft.workItemId(), change.reservedIds()));
            entry.setType(normalizeType(valueOrDefault(draft.type(), "COMMENT")));
            entry.setBody(draft.body().trim());
        } else {
            previous = entryService.get(change.projectId(), change.targetId());
            entry = copyEntry(previous);
            if (!"DELETE".equals(change.action())) {
                if (draft == null) throw createBadRequestException("Entry updates require proposed values");
                if (!isBlank(draft.type())) entry.setType(normalizeType(draft.type()));
                if (!isBlank(draft.body())) entry.setBody(draft.body().trim());
            }
        }

        return new WorkspacePreparedChange(
                JsonUtils.toJson(entry),
                previous == null ? null : JsonUtils.toJson(previous),
                createBaseVersion(previous == null ? null : previous.getUpdatedAt()));
    }

    @Override
    public void apply(WorkspaceProposalChange change, WorkspacePreparedChange prepared, AppUser actor) {
        Entry payload = JsonUtils.fromJson(prepared.payloadJson(), Entry.class);
        if ("ADD".equals(change.action())) {
            entryService.createWithId(change.projectId(), change.targetId(), payload, actor.getId());
            return;
        }

        WorkspaceProposalVersion version = JsonUtils.fromJson(
                prepared.baseVersionJson(), WorkspaceProposalVersion.class);
        Entry current = entryService.get(change.projectId(), change.targetId());
        if (!Objects.equals(current.getUpdatedAt(), version.updatedAt())) {
            throw createConflictException("Entry changed after this AI suggestion was created. Ask AI to review it again.");
        }
        if ("UPDATE".equals(change.action())) {
            entryService.update(change.projectId(), change.targetId(), payload, actor.getId());
        } else {
            entryService.delete(change.projectId(), change.targetId(), actor.getId());
        }
    }

    @Override
    public WorkspaceProposalChange buildRevert(ProposalChange appliedChange, AppUser actor) {
        if (!"UPDATE".equals(appliedChange.getOperation())) {
            throw createBadRequestException("Entry creation or deletion cannot be safely reverted because related data is not fully captured");
        }
        WorkspaceProposalTarget target = JsonUtils.fromJson(appliedChange.getTargetRef(), WorkspaceProposalTarget.class);
        Entry before = JsonUtils.fromJson(appliedChange.getBeforeSnapshot(), Entry.class);
        Entry after = JsonUtils.fromJson(appliedChange.getAfterSnapshot(), Entry.class);
        Entry current = entryService.get(target.projectId(), target.targetId());
        String type = buildRevertValue("entry type", current.getType(), before.getType(), after.getType());
        String body = buildRevertValue("entry body", current.getBody(), before.getBody(), after.getBody());
        if (type == null && body == null) {
            throw createBadRequestException("This entry proposal has no reversible changes");
        }
        EntryDraft draft = new EntryDraft(null, type, body);
        ChangeDraft changeDraft = new ChangeDraft("ENTRY", "UPDATE", target.targetId(), null,
                "Revert " + target.summary(), null, draft, null);
        return new WorkspaceProposalChange(target.projectId(), "UPDATE", target.targetId(),
                changeDraft.summary(), changeDraft, Map.of());
    }

    private String buildRevertValue(String label, String current, String before, String after) {
        if (Objects.equals(before, after)) return null;
        if (!Objects.equals(current, after)) {
            throw createConflictException("Cannot safely revert because " + label + " changed after the proposal was applied. "
                    + "Create a new proposal from the current value if you still want to restore it");
        }
        return before;
    }

    private Entry copyEntry(Entry source) {
        Entry copy = new Entry();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setWorkItemId(source.getWorkItemId());
        copy.setSortIndex(source.getSortIndex());
        copy.setAuthorUserId(source.getAuthorUserId());
        copy.setAuthorDisplayName(source.getAuthorDisplayName());
        copy.setType(source.getType());
        copy.setBody(source.getBody());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private String resolveRef(String value, Map<String, String> reservedIds) {
        if (isBlank(value)) throw createBadRequestException("Entity reference is required");
        String normalized = value.trim();
        return reservedIds.getOrDefault(normalized, normalized);
    }

    private String normalizeType(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase();
        if (normalized == null || !WorkTypes.ENTRY_TYPES.contains(normalized)) {
            throw createBadRequestException("Entry type is invalid");
        }
        return normalized;
    }

    private String createBaseVersion(OffsetDateTime updatedAt) {
        return JsonUtils.toJson(new WorkspaceProposalVersion(updatedAt));
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException createBadRequestException(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException createConflictException(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
