package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchWorkItemsTool implements Tool<FetchWorkItemsTool.Parameters> {
    private final WorkItemService workItems;
    private final ProjectSearchService search;

    @Override
    public String name() {
        return "fetch_work_items";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-work-items-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters) {
        if (parameters == null || parameters.projectId() == null || parameters.projectId().isBlank())
            throw new IllegalArgumentException("projectId is required");
        String query = parameters.query() == null ? "" : parameters.query().trim();
        int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        List<Result> items = (query.isEmpty() ? workItems.list(parameters.projectId()) : search.search(parameters.projectId(), query, limit).workItems())
                .stream().limit(limit).map(item -> new Result(item, workItems.assignees(item.getId()))).toList();
        return new Response(items, items.size(), limit);
    }

    public record Parameters(String projectId, String query, Integer limit) {
    }

    public record Result(WorkItem workItem, List<WorkItemAssignee> assignees) {
    }

    public record Response(List<Result> workItems, int count, int limit) {
    }
}
