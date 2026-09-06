package com.windrunner.server.proposal;

import java.util.Map;

public record ProposalChangeView(
        String id,
        ProposalKind kind,
        String action,
        String status,
        Map<String, String> before,
        Map<String, String> after
) {
}
