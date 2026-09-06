package com.windrunner.server.proposal;

import java.util.List;
import java.util.Map;

public record ProposalDraft(
        String action,
        String teamId,
        String userId,
        String projectId,
        String subjectType,
        String role,
        List<String> ownerUserIds,
        Map<String, String> fields
) {
}
