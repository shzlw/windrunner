package com.windrunner.server.work.proposal;

import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalWorkflow;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class WorkspaceProposalWorkflow implements ProposalWorkflow<WorkspaceProposalChange, WorkspacePreparedChange> {
    public static final String WORKFLOW_TYPE = "WORKSPACE";

    private final Map<String, ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange>> handlers;

    public WorkspaceProposalWorkflow(WorkItemProposalHandler workItem,
                                     EntryProposalHandler entry,
                                     RelationshipProposalHandler relationship) {
        handlers = Map.of(
                workItem.getEntityType(), workItem,
                entry.getEntityType(), entry,
                relationship.getEntityType(), relationship);
    }

    @Override
    public String getWorkflowType() {
        return WORKFLOW_TYPE;
    }

    @Override
    public ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> handler(String entityType) {
        ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> handler = handlers.get(entityType);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported workspace proposal entity type: " + entityType);
        }
        return handler;
    }
}
