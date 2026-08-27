package com.windrunner.server.work.api;

import java.util.List;

public record WorkItemSubtreeView(List<WorkItemView> items, boolean truncated) {
}
