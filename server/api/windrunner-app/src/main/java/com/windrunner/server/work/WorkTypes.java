package com.windrunner.server.work;

import java.util.Set;

public final class WorkTypes {
    public static final Set<String> WORK_ITEM_TYPES = Set.of("TASK", "QUESTION", "APPROVAL", "REVIEW", "DECISION");
    public static final Set<String> ENTRY_TYPES = Set.of("COMMENT", "INFORMATION", "ANSWER", "EVIDENCE", "PROPOSAL", "RESOLUTION");
    public static final Set<String> RELATIONSHIP_TYPES = Set.of("BLOCKED_BY", "DEPENDS_ON", "RELATED_TO", "ANSWERS", "SUPPORTS", "CONTRADICTS", "RESOLVES", "SUPERSEDES", "ACCEPTED_ANSWER");
    public static final Set<String> WORK_ITEM_STATUSES = Set.of(
            "OPEN", "IN_PROGRESS", "BLOCKED", "DONE", "WAITING", "ANSWERED",
            "PENDING", "APPROVED", "REJECTED", "CANCELLED");
    public static final Set<String> ASSIGNEE_TYPES = Set.of("USER", "TEAM");
    public static final Set<String> ENTITY_TYPES = Set.of("WORK_ITEM", "ENTRY");
    private WorkTypes() { }
}
