package com.windrunner.server.agent.api;

import com.windrunner.server.work.api.WorkspaceChangeProposalView;

import java.util.List;

public record AgentMessageResponse(
        String requestId,
        String reply,
        Routing routing,
        String state,
        List<WorkspaceChangeProposalView> proposals,
        String error
) {
    public record Routing(
            String decision,
            ChatSessionReference chatSession
    ) { }

    public record ChatSessionReference(String id, String title) { }
}
