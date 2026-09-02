package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import com.windrunner.server.search.SearchNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FetchWorkItemsTool implements Tool<FetchWorkItemsTool.Parameters> {
    private final WorkItemRepository workItems;
    private final WorkItemAssigneeRepository assignees;
    private final SearchNormalizer searchNormalizer;
    private final ToolAuthorizationService authorization;

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
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(
                context, parameters == null ? null : parameters.projectId());
        String query = parameters.query() == null ? "" : parameters.query().trim();
        int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        long offset = parameters.offset() == null ? 0 : Math.max(0, parameters.offset());
        List<WorkItem> page;
        long total;
        if (query.isEmpty()) {
            page = workItems.findPageForProject(projectId, null, null, null, null, limit, offset);
            total = workItems.countForProject(projectId, null, null, null, null);
        } else {
            String ftsQuery = searchNormalizer.normalize(query);
            page = workItems.searchInProjectPage(projectId, ftsQuery, query, limit, offset);
            total = workItems.countSearchInProject(projectId, ftsQuery, query);
        }
        Map<String, List<WorkItemAssignee>> assigneesByWorkItemId = new HashMap<>();
        if (!page.isEmpty()) {
            assignees.findByWorkItemIds(page.stream().map(WorkItem::getId).toList())
                    .forEach(assignee -> assigneesByWorkItemId
                            .computeIfAbsent(assignee.getWorkItemId(), ignored -> new java.util.ArrayList<>())
                            .add(assignee));
        }
        List<Result> items = page.stream()
                .map(item -> new Result(item, assigneesByWorkItemId.getOrDefault(item.getId(), List.of())))
                .toList();
        return new Response(items, items.size(), total, limit, offset, offset + items.size() < total);
    }

    public record Parameters(String projectId, String query, Integer limit, Integer offset) {
    }

    public record Result(WorkItem workItem, List<WorkItemAssignee> assignees) {
    }

    public record Response(List<Result> workItems, int count, long total, int limit, long offset, boolean hasMore) {
    }
}
