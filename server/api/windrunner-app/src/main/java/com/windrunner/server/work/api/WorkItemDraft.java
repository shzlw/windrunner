package com.windrunner.server.work.api;

import java.util.List;

public record WorkItemDraft(
        String title,
        String type,
        String status,
        String dueDate,
        String priority,
        String parentWorkItemId,
        List<AssigneeDraft> assignees
) {
}
