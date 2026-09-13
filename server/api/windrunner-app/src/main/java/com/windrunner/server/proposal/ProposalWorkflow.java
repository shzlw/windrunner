package com.windrunner.server.proposal;

/**
 * Selects typed handlers while leaving workflow-specific status transitions to the workflow service.
 */
public interface ProposalWorkflow<T, P> {
    String getWorkflowType();

    ProposalHandler<T, P> handler(String entityType);
}
