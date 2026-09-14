package com.windrunner.server.work;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.proposal.Proposal;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalChangeRepository;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.WorkItemDraft;
import com.windrunner.server.work.api.WorkItemPayload;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.proposal.WorkspacePreparedChange;
import com.windrunner.server.work.proposal.WorkspaceProposalChange;
import com.windrunner.server.work.proposal.WorkspaceProposalTarget;
import com.windrunner.server.work.proposal.WorkspaceProposalWorkflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceChangeProposalServiceTest {

    @Mock
    private ProposalRepository proposalRepository;
    @Mock
    private ProposalChangeRepository proposalChangeRepository;
    @Mock
    private WorkspaceProposalWorkflow proposalWorkflow;
    @Mock
    private EntityIdGenerator entityIdGenerator;
    @Mock
    private ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> proposalHandler;

    @Test
    void createsWorkspaceChangesInTheUnifiedProposalTables() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        ChangeDraft changeDraft = new ChangeDraft(
                "WORK_ITEM", "ADD", null, "new-task", "Create work item",
                new WorkItemDraft("Task", "TASK", "OPEN", null, null, null, List.of()),
                null, null);
        WorkItem workItem = new WorkItem();
        workItem.setId("work-item-1");
        workItem.setProjectId("project-1");
        workItem.setTitle("Task");
        String payload = JsonUtils.toJson(new WorkItemPayload(workItem, List.of()));
        Proposal proposal = new Proposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setStatus("PENDING");
        ProposalChange storedChange = createChange("change-1", "PENDING");
        storedChange.setOperation("ADD");
        storedChange.setPayload(payload);

        when(entityIdGenerator.generate(EntityIdType.WORK_ITEM))
                .thenReturn("work-item-1");
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL))
                .thenReturn("proposal-1");
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE))
                .thenReturn("change-1");
        when(proposalWorkflow.handler("WORK_ITEM")).thenReturn(proposalHandler);
        when(proposalHandler.prepare(any(), same(actor)))
                .thenReturn(new WorkspacePreparedChange(payload, null, "{}"));
        when(proposalRepository.findWorkspaceInProject("proposal-1", "project-1"))
                .thenReturn(Optional.of(proposal));
        when(proposalChangeRepository.findByProposalId("proposal-1")).thenReturn(List.of(storedChange));

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        service.create("project-1", "chat-1", "message-1", "Create it", actor,
                new ProposalDraft(List.of(changeDraft)));

        verify(proposalRepository).insertWorkspace(
                "proposal-1", "project-1", "chat-1", "message-1", "Create it", "actor-1");
        verify(proposalChangeRepository).insert(
                "change-1", "proposal-1", 0, "WORK_ITEM", "ADD",
                JsonUtils.toJson(new WorkspaceProposalTarget("project-1", "work-item-1", "Create work item")),
                payload, "{}", payload, "{}");
    }

    @Test
    void createsMcpWorkspaceChangesForTheExactApiKey() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        ChangeDraft changeDraft = new ChangeDraft(
                "WORK_ITEM", "UPDATE", "work-item-1", null, "Update work item",
                new WorkItemDraft("Updated task", null, null, null, null, null, null),
                null, null);
        String payload = createWorkItemPayload("Updated task");
        Proposal proposal = new Proposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setStatus("PENDING");
        ProposalChange storedChange = createChange("change-1", "PENDING");
        storedChange.setPayload(payload);

        when(entityIdGenerator.generate(EntityIdType.PROPOSAL)).thenReturn("proposal-1");
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE)).thenReturn("change-1");
        when(proposalWorkflow.handler("WORK_ITEM")).thenReturn(proposalHandler);
        when(proposalHandler.prepare(any(), same(actor)))
                .thenReturn(new WorkspacePreparedChange(payload, "{}", "{}"));
        when(proposalRepository.findMcpWorkspaceInProject("proposal-1", "project-1", "api-key-1"))
                .thenReturn(Optional.of(proposal));
        when(proposalChangeRepository.findByProposalId("proposal-1")).thenReturn(List.of(storedChange));

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        service.createFromMcp("project-1", "Update it", "api-key-1", actor,
                new ProposalDraft(List.of(changeDraft)));

        verify(proposalRepository).insertMcpWorkspace(
                "proposal-1", "project-1", "Update it", "api-key-1", "actor-1");
        verify(proposalRepository).findMcpWorkspaceInProject("proposal-1", "project-1", "api-key-1");
    }

    @Test
    void listsCompactMcpProposalSummariesWithoutLoadingChangePayloads() {
        Proposal proposal = new Proposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setSourceText("Update it");
        proposal.setStatus("PENDING");
        when(proposalRepository.pageMcpWorkspace("api-key-1", "project-1", "PENDING", 20, 0))
                .thenReturn(List.of(proposal));
        when(proposalRepository.countMcpWorkspace("api-key-1", "project-1", "PENDING")).thenReturn(1L);

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        var result = service.listFromMcp("project-1", "api-key-1", "pending", null, null);

        assertThat(result.proposals()).hasSize(1);
        assertThat(result.proposals().getFirst().id()).isEqualTo("proposal-1");
        assertThat(result.hasMore()).isFalse();
        verifyNoInteractions(proposalChangeRepository);
    }

    @Test
    void rejectsEveryOpenChangeInOneDecision() {
        Proposal proposal = new Proposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setStatus("PENDING");
        ProposalChange first = createChange("change-1", "PENDING");
        ProposalChange second = createChange("change-2", "NEEDS_UPDATE");
        ProposalChange rejectedFirst = createChange("change-1", "REJECTED");
        ProposalChange rejectedSecond = createChange("change-2", "REJECTED");
        AppUser actor = new AppUser();
        actor.setId("actor-1");

        when(proposalRepository.findWorkspaceInProjectForUpdate("proposal-1", "project-1"))
                .thenReturn(Optional.of(proposal));
        when(proposalRepository.findWorkspaceInProject("proposal-1", "project-1"))
                .thenReturn(Optional.of(proposal));
        when(proposalChangeRepository.findByProposalId("proposal-1"))
                .thenReturn(List.of(first, second), List.of(rejectedFirst, rejectedSecond),
                        List.of(rejectedFirst, rejectedSecond));
        when(proposalChangeRepository.decide("change-1", "proposal-1", "REJECTED", null)).thenReturn(1);
        when(proposalChangeRepository.decide("change-2", "proposal-1", "REJECTED", null)).thenReturn(1);
        when(proposalRepository.updateWorkspaceStatus("proposal-1", "project-1", "REJECTED", "actor-1"))
                .thenReturn(1);

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        service.decideAll("project-1", "proposal-1", new DecisionRequest("REJECT", null), actor);

        verify(proposalChangeRepository).decide("change-1", "proposal-1", "REJECTED", null);
        verify(proposalChangeRepository).decide("change-2", "proposal-1", "REJECTED", null);
        verify(proposalRepository).updateWorkspaceStatus("proposal-1", "project-1", "REJECTED", "actor-1");
    }

    @Test
    void decidesOnlyAnMcpProposalOwnedByTheExactApiKey() {
        Proposal proposal = new Proposal();
        proposal.setId("proposal-1");
        proposal.setProjectId("project-1");
        proposal.setStatus("PENDING");
        ProposalChange pending = createChange("change-1", "PENDING");
        ProposalChange rejected = createChange("change-1", "REJECTED");
        AppUser actor = new AppUser();
        actor.setId("actor-1");

        when(proposalRepository.findMcpWorkspaceInProjectForUpdate(
                "proposal-1", "project-1", "api-key-1")).thenReturn(Optional.of(proposal));
        when(proposalRepository.findMcpWorkspaceInProject(
                "proposal-1", "project-1", "api-key-1")).thenReturn(Optional.of(proposal));
        when(proposalChangeRepository.findByProposalId("proposal-1"))
                .thenReturn(List.of(pending), List.of(rejected), List.of(rejected));
        when(proposalChangeRepository.decide("change-1", "proposal-1", "REJECTED", null)).thenReturn(1);
        when(proposalRepository.updateWorkspaceStatus("proposal-1", "project-1", "REJECTED", "actor-1"))
                .thenReturn(1);

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        service.decideAllFromMcp("project-1", "proposal-1", new DecisionRequest("REJECT", null),
                "api-key-1", actor);

        verify(proposalRepository).findMcpWorkspaceInProjectForUpdate(
                "proposal-1", "project-1", "api-key-1");
        verify(proposalRepository, never()).findWorkspaceInProjectForUpdate(any(), any());
    }

    @Test
    void createsARevertAsAnotherPendingWorkspaceProposal() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        Proposal original = new Proposal();
        original.setId("proposal-1");
        original.setProjectId("project-1");
        original.setChatSessionId("chat-1");
        original.setSourceMessageId("message-1");
        original.setSourceText("Update it");
        original.setStatus("APPLIED");
        ProposalChange applied = createChange("change-1", "APPLIED");
        ChangeDraft revertDraft = new ChangeDraft(
                "WORK_ITEM", "UPDATE", "work-item-1", null, "Revert update",
                new WorkItemDraft("Old title", null, null, null, null, null, null),
                null, null);
        WorkspaceProposalChange revertChange = new WorkspaceProposalChange(
                "project-1", "UPDATE", "work-item-1", "Revert update", revertDraft, Map.of());
        WorkspacePreparedChange prepared = new WorkspacePreparedChange(applied.getPayload(), applied.getPayload(), "{}");
        Proposal created = new Proposal();
        created.setId("proposal-2");
        created.setProjectId("project-1");
        created.setRevertsProposalId("proposal-1");
        created.setStatus("PENDING");
        ProposalChange storedRevert = createChange("change-2", "PENDING");
        storedRevert.setProposalId("proposal-2");

        when(proposalRepository.findWorkspaceInProjectForUpdate("proposal-1", "project-1"))
                .thenReturn(Optional.of(original));
        when(proposalRepository.findActiveRevert("proposal-1")).thenReturn(Optional.empty());
        when(proposalChangeRepository.findByProposalId("proposal-1")).thenReturn(List.of(applied));
        when(proposalWorkflow.handler("WORK_ITEM")).thenReturn(proposalHandler);
        when(proposalHandler.buildRevert(applied, actor)).thenReturn(revertChange);
        when(proposalHandler.prepare(revertChange, actor)).thenReturn(prepared);
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL)).thenReturn("proposal-2");
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE)).thenReturn("change-2");
        when(proposalRepository.findWorkspaceInProject("proposal-2", "project-1"))
                .thenReturn(Optional.of(created));
        when(proposalChangeRepository.findByProposalId("proposal-2")).thenReturn(List.of(storedRevert));

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        var result = service.createRevert("project-1", "proposal-1", actor);

        assertThat(result.revertsProposalId()).isEqualTo("proposal-1");
        verify(proposalRepository).insertWorkspaceRevert(
                "proposal-2", "project-1", "chat-1", "message-1", "Update it", "proposal-1", "actor-1");
        verify(proposalChangeRepository).insert(eq("change-2"), eq("proposal-2"), eq(0), eq("WORK_ITEM"),
                eq("UPDATE"), any(), eq(prepared.payloadJson()), eq(prepared.previousJson()),
                eq(prepared.payloadJson()), eq(prepared.baseVersionJson()));
    }

    @Test
    void createsMcpRevertForTheExactApiKey() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        Proposal original = new Proposal();
        original.setId("proposal-1");
        original.setProjectId("project-1");
        original.setSourceText("Update it");
        original.setStatus("APPLIED");
        ProposalChange applied = createChange("change-1", "APPLIED");
        ChangeDraft revertDraft = new ChangeDraft(
                "WORK_ITEM", "UPDATE", "work-item-1", null, "Revert update",
                new WorkItemDraft("Old title", null, null, null, null, null, null),
                null, null);
        WorkspaceProposalChange revertChange = new WorkspaceProposalChange(
                "project-1", "UPDATE", "work-item-1", "Revert update", revertDraft, Map.of());
        WorkspacePreparedChange prepared = new WorkspacePreparedChange(applied.getPayload(), applied.getPayload(), "{}");
        Proposal created = new Proposal();
        created.setId("proposal-2");
        created.setProjectId("project-1");
        created.setRevertsProposalId("proposal-1");
        created.setStatus("PENDING");
        ProposalChange storedRevert = createChange("change-2", "PENDING");
        storedRevert.setProposalId("proposal-2");

        when(proposalRepository.findMcpWorkspaceInProjectForUpdate(
                "proposal-1", "project-1", "api-key-1")).thenReturn(Optional.of(original));
        when(proposalRepository.findActiveRevert("proposal-1")).thenReturn(Optional.empty());
        when(proposalChangeRepository.findByProposalId("proposal-1")).thenReturn(List.of(applied));
        when(proposalWorkflow.handler("WORK_ITEM")).thenReturn(proposalHandler);
        when(proposalHandler.buildRevert(applied, actor)).thenReturn(revertChange);
        when(proposalHandler.prepare(revertChange, actor)).thenReturn(prepared);
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL)).thenReturn("proposal-2");
        when(entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE)).thenReturn("change-2");
        when(proposalRepository.findMcpWorkspaceInProject(
                "proposal-2", "project-1", "api-key-1")).thenReturn(Optional.of(created));
        when(proposalChangeRepository.findByProposalId("proposal-2")).thenReturn(List.of(storedRevert));

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);
        var result = service.createRevertFromMcp("project-1", "proposal-1", "api-key-1", actor);

        assertThat(result.revertsProposalId()).isEqualTo("proposal-1");
        verify(proposalRepository).insertMcpWorkspaceRevert(
                "proposal-2", "project-1", "Update it", "api-key-1", "proposal-1", "actor-1");
    }

    @Test
    void rejectsAnotherRevertWhileOneIsActive() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        Proposal original = new Proposal();
        original.setId("proposal-1");
        original.setStatus("APPLIED");
        Proposal existingRevert = new Proposal();
        existingRevert.setId("proposal-2");
        existingRevert.setStatus("PENDING");
        when(proposalRepository.findWorkspaceInProjectForUpdate("proposal-1", "project-1"))
                .thenReturn(Optional.of(original));
        when(proposalRepository.findActiveRevert("proposal-1")).thenReturn(Optional.of(existingRevert));

        WorkspaceChangeProposalService service = new WorkspaceChangeProposalService(
                proposalRepository, proposalChangeRepository, proposalWorkflow, entityIdGenerator);

        assertThatThrownBy(() -> service.createRevert("project-1", "proposal-1", actor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(proposalWorkflow, never()).handler(any());
    }

    private ProposalChange createChange(String id, String status) {
        ProposalChange change = new ProposalChange();
        change.setId(id);
        change.setProposalId("proposal-1");
        change.setSortIndex(0);
        change.setEntityType("WORK_ITEM");
        change.setOperation("UPDATE");
        change.setTargetRef(JsonUtils.toJson(
                new WorkspaceProposalTarget("project-1", "work-item-1", "Update work item")));
        change.setStatus(status);
        WorkItem workItem = new WorkItem();
        workItem.setId("work-item-1");
        workItem.setProjectId("project-1");
        workItem.setTitle("Work item");
        change.setPayload(JsonUtils.toJson(new WorkItemPayload(workItem, List.of())));
        change.setBeforeSnapshot("{}");
        return change;
    }

    private String createWorkItemPayload(String title) {
        WorkItem workItem = new WorkItem();
        workItem.setId("work-item-1");
        workItem.setProjectId("project-1");
        workItem.setTitle(title);
        return JsonUtils.toJson(new WorkItemPayload(workItem, List.of()));
    }
}
