package com.windrunner.server.work;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkspaceChangeProposal;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceChangeProposalServiceTest {

    @Mock
    private WorkspaceChangeProposalRepository proposals;
    @Mock
    private WorkspaceChangeRepository changes;
    @Mock
    private WorkItemService workItems;
    @Mock
    private EntryService entries;
    @Mock
    private RelationshipService relationships;
    @Mock
    private EntityIdGenerator ids;

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

        when(ids.generate(EntityIdType.WORKSPACE_CHANGE_PROPOSAL)).thenReturn("proposal-1");
        when(ids.generate(EntityIdType.WORKSPACE_CHANGE)).thenReturn("change-1");
        when(relationships.list("project-1")).thenReturn(List.of(current));
        when(proposals.findInProject("proposal-1", "project-1")).thenReturn(Optional.of(proposal));
        when(changes.findByProposalId("proposal-1")).thenReturn(List.of());

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposals, changes, workItems, entries, relationships, ids);
        var relationshipDraft = new WorkspaceChangeProposalService.RelationshipDraft(
                null, null, null, null, null, requestedReason, null);
        var changeDraft = new WorkspaceChangeProposalService.ChangeDraft(
                "RELATIONSHIP", "UPDATE", "relationship-1", null, "Update relationship", null, null,
                relationshipDraft);
        service.create("project-1", "chat-1", "message-1", "Update it",
                new WorkspaceChangeProposalService.ProposalDraft(List.of(changeDraft)));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(changes).insert(eq("change-1"), eq("proposal-1"), eq("project-1"), anyInt(),
                eq("RELATIONSHIP"), eq("UPDATE"), eq("relationship-1"), anyString(), payload.capture(), any());
        return JsonUtils.fromJson(payload.getValue(), Relationship.class);
    }
}
