package com.windrunner.server.work;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkspaceChangeProposal;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.RelationshipDraft;
import com.windrunner.server.work.api.WorkItemPayload;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkspaceChange;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceChangeProposalServiceTest {

    @Mock
    private WorkspaceChangeProposalRepository workspaceChangeProposalRepository;
    @Mock
    private WorkspaceChangeRepository workspaceChangeRepository;
    @Mock
    private WorkItemService workItems;
    @Mock
    private EntryService entries;
    @Mock
    private RelationshipService relationships;
    @Mock
    private EntityIdGenerator entityIdGenerator;

    @Test
    void relationshipUpdateWithNullReasonPreservesExistingReason() {
        Relationship proposed = createRelationshipUpdate(null);

        assertThat(proposed.getReason()).isEqualTo("Existing reason");
    }

    @Test
    void relationshipUpdateWithBlankReasonClearsExistingReason() {
        Relationship proposed = createRelationshipUpdate("  ");

        assertThat(proposed.getReason()).isNull();
    }

    @Test
    void rejectsEveryOpenChangeInOneDecision() {
        WorkspaceChangeProposal proposal = new WorkspaceChangeProposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setStatus("PENDING");
        WorkspaceChange first = createChange("change-1", "PENDING");
        WorkspaceChange second = createChange("change-2", "NEEDS_UPDATE");
        WorkspaceChange rejectedFirst = createChange("change-1", "REJECTED");
        WorkspaceChange rejectedSecond = createChange("change-2", "REJECTED");

        when(workspaceChangeProposalRepository.findInProjectForUpdate("proposal-1", "project-1"))
                .thenReturn(Optional.of(proposal));
        when(workspaceChangeProposalRepository.findInProject("proposal-1", "project-1"))
                .thenReturn(Optional.of(proposal));
        when(workspaceChangeRepository.findByProposalId("proposal-1"))
                .thenReturn(List.of(first, second), List.of(rejectedFirst, rejectedSecond));

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                workspaceChangeProposalRepository, workspaceChangeRepository, workItems, entries, relationships, entityIdGenerator);
        service.decideAll("project-1", "proposal-1", new DecisionRequest("REJECT", null), "actor-1");

        verify(workspaceChangeRepository).decide("change-1", "proposal-1", "project-1", "REJECTED", null);
        verify(workspaceChangeRepository).decide("change-2", "proposal-1", "project-1", "REJECTED", null);
        verify(workspaceChangeProposalRepository).updateStatus("proposal-1", "project-1", "COMPLETED");
    }

    private WorkspaceChange createChange(String id, String status) {
        WorkspaceChange change = new WorkspaceChange();
        change.setId(id);
        change.setProposalId("proposal-1");
        change.setProjectId("project-1");
        change.setSortIndex(0);
        change.setEntityType("WORK_ITEM");
        change.setAction("UPDATE");
        change.setTargetId("work-item-1");
        change.setSummary("Update work item");
        change.setStatus(status);
        WorkItem workItem = new WorkItem();
        workItem.setId("work-item-1");
        workItem.setProjectId("project-1");
        workItem.setTitle("Work item");
        change.setPayloadJson(JsonUtils.toJson(new WorkItemPayload(workItem, List.of())));
        return change;
    }

    private Relationship createRelationshipUpdate(String requestedReason) {
        Relationship current = new Relationship();
        current.setId("relationship-1");
        current.setProjectId("project-1");
        current.setFromEntityType("WORK_ITEM");
        current.setFromEntityId("work-item-1");
        current.setToEntityType("WORK_ITEM");
        current.setToEntityId("work-item-2");
        current.setType("BLOCKS");
        current.setReason("Existing reason");

        WorkspaceChangeProposal proposal = new WorkspaceChangeProposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setStatus("PENDING");

        when(entityIdGenerator.generate(EntityIdType.WORKSPACE_CHANGE_PROPOSAL)).thenReturn("proposal-1");
        when(entityIdGenerator.generate(EntityIdType.WORKSPACE_CHANGE)).thenReturn("change-1");
        when(relationships.list("project-1")).thenReturn(List.of(current));
        when(workspaceChangeProposalRepository.findInProject("proposal-1", "project-1")).thenReturn(Optional.of(proposal));
        when(workspaceChangeRepository.findByProposalId("proposal-1")).thenReturn(List.of());

        WorkspaceChangeProposalService workspaceChangeProposalService = new WorkspaceChangeProposalService(
                workspaceChangeProposalRepository, workspaceChangeRepository, workItems, entries, relationships, entityIdGenerator);
        var relationshipDraft = new RelationshipDraft(
                null, null, null, null, null, requestedReason, null);
        var changeDraft = new ChangeDraft(
                "RELATIONSHIP", "UPDATE", "relationship-1", null, "Update relationship", null, null,
                relationshipDraft);
        workspaceChangeProposalService.create("project-1", "chat-1", "message-1", "Update it",
                new ProposalDraft(List.of(changeDraft)));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(workspaceChangeRepository).insert(eq("change-1"), eq("proposal-1"), eq("project-1"), anyInt(),
                eq("RELATIONSHIP"), eq("UPDATE"), eq("relationship-1"), anyString(), payload.capture(), any());
        return JsonUtils.fromJson(payload.getValue(), Relationship.class);
    }
}
