package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.proposal.ProposalWorkflow;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class IdentityProposalWorkflow implements ProposalWorkflow<ProposalDraft> {
    public static final String WORKFLOW_TYPE = "IDENTITY";

    private final Map<String, ProposalHandler<ProposalDraft>> handlers;

    public IdentityProposalWorkflow(TeamProposalHandler team,
                                    TeamMembershipProposalHandler teamMembership,
                                    ProjectMembershipProposalHandler projectMembership,
                                    UserProfileProposalHandler userProfile,
                                    UserAccessProposalHandler userAccess) {
        this.handlers = Map.of(
                team.getEntityType(), team,
                teamMembership.getEntityType(), teamMembership,
                projectMembership.getEntityType(), projectMembership,
                userProfile.getEntityType(), userProfile,
                userAccess.getEntityType(), userAccess);
    }

    @Override
    public String getWorkflowType() {
        return WORKFLOW_TYPE;
    }

    @Override
    public ProposalHandler<ProposalDraft> handler(String entityType) {
        ProposalHandler<ProposalDraft> handler = handlers.get(entityType);
        if (handler == null)
            throw new IllegalArgumentException("Unsupported identity proposal entity type: " + entityType);
        return handler;
    }
}
