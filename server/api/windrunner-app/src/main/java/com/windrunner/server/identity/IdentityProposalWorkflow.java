package com.windrunner.server.identity;

import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalWorkflow;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class IdentityProposalWorkflow implements ProposalWorkflow<IdentityProposalService.Draft> {
    public static final String WORKFLOW_TYPE = "IDENTITY";

    private final Map<String, ProposalHandler<IdentityProposalService.Draft>> handlers;

    public IdentityProposalWorkflow(TeamProposalHandler team,
                                    TeamMembershipProposalHandler teamMembership,
                                    ProjectMembershipProposalHandler projectMembership,
                                    UserProfileProposalHandler userProfile,
                                    UserAccessProposalHandler userAccess) {
        this.handlers = Map.of(
                team.entityType(), team,
                teamMembership.entityType(), teamMembership,
                projectMembership.entityType(), projectMembership,
                userProfile.entityType(), userProfile,
                userAccess.entityType(), userAccess);
    }

    @Override
    public String workflowType() {
        return WORKFLOW_TYPE;
    }

    @Override
    public ProposalHandler<IdentityProposalService.Draft> handler(String entityType) {
        ProposalHandler<IdentityProposalService.Draft> handler = handlers.get(entityType);
        if (handler == null) throw new IllegalArgumentException("Unsupported identity proposal entity type: " + entityType);
        return handler;
    }
}
