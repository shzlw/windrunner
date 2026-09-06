package com.windrunner.server.proposal;

import java.util.List;

public record ProposalCreated(
        String id,
        ProposalKind kind,
        String action,
        String status,
        List<String> changedFields
) {
}
