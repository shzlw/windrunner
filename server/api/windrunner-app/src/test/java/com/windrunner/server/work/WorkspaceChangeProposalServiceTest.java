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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
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
}
