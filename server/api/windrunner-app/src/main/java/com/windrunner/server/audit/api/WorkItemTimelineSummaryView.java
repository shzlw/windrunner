package com.windrunner.server.audit.api;

import java.util.List;

public record WorkItemTimelineSummaryView(
        String projectId,
        String workItemId,
        String title,
        int count,
        List<WorkItemTimelineEvent> events
) {
}
