package com.windrunner.server.work.api;

import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;

import java.util.List;

public record ProjectSearchResult(
        List<WorkItem> workItems,
        List<Entry> entries,
        List<Relationship> relationships
) {
}
