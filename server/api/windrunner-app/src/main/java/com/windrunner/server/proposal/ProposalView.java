package com.windrunner.server.proposal;

import java.util.List;
import java.util.Map;

/** The first-change fields remain for API compatibility; changes contains the complete batch. */
public record ProposalView(
        String id,
        String sourceMessageId,
        String workflowType,
        ProposalKind kind,
        String action,
        String status,
        Map<String, String> before,
        Map<String, String> after,
        List<ProposalChangeView> changes
) {
}
