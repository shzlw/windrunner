package com.windrunner.server.work.api;

import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import java.util.List;

public record WorkspaceView(List<WorkItemView> workItems, List<Entry> entries, List<Relationship> relationships) { }
