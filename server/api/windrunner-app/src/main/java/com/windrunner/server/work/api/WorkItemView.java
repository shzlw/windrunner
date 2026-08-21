package com.windrunner.server.work.api;

import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;

import java.util.List;

public record WorkItemView(WorkItem workItem, List<WorkItemAssignee> assignees) {
}
