package com.windrunner.server.work.api;

import com.windrunner.server.work.domain.WorkItemAssignee;

import java.util.List;

public record WorkItemAiReviewRequest(String title, String type, String status, String dueDate, String priority,
                                      List<WorkItemAssignee> assignees, String instruction) {
}
