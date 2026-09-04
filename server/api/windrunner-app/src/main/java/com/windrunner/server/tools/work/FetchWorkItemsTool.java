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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

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
    public boolean parallelSafe() {
        return true;
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
        String query = parameters == null || parameters.query() == null ? "" : parameters.query().trim();
        String parentWorkItemId = normalizeParent(parameters == null ? null : parameters.parentWorkItemId());
        String type = normalizeType(parameters == null ? null : parameters.type());
        int limit = parameters == null || parameters.limit() == null
                ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        long offset = parameters == null || parameters.offset() == null
                ? 0 : Math.max(0, parameters.offset());
        List<WorkItem> page;
        long total;
        if (query.isEmpty()) {
            page = workItems.findPageForProjectWithFilters(projectId, parentWorkItemId, type, limit, offset);
            total = workItems.countForProjectWithFilters(projectId, parentWorkItemId, type);
        } else {
            String ftsQuery = searchNormalizer.normalize(query);
            page = workItems.searchInProjectPageWithFilters(
                    projectId, ftsQuery, query, parentWorkItemId, type, limit, offset);
            total = workItems.countSearchInProjectWithFilters(
                    projectId, ftsQuery, query, parentWorkItemId, type);
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

    private String normalizeParent(String parentWorkItemId) {
        if (parentWorkItemId == null || parentWorkItemId.isBlank()) {
            return null;
        }
        String normalized = parentWorkItemId.trim();
        return "PROJECT_ROOT".equalsIgnoreCase(normalized) ? "PROJECT_ROOT" : normalized;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!com.windrunner.server.work.WorkTypes.WORK_ITEM_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is invalid");
        }
        return normalized;
    }

    public record Parameters(String projectId, String query, String parentWorkItemId, String type,
                             Integer limit, Integer offset) {
        public Parameters(String projectId, String query, Integer limit, Integer offset) {
            this(projectId, query, null, null, limit, offset);
        }
    }

    public record Result(WorkItem workItem, List<WorkItemAssignee> assignees) {
    }

    public record Response(List<Result> workItems, int count, long total, int limit, long offset, boolean hasMore) {
    }
}
