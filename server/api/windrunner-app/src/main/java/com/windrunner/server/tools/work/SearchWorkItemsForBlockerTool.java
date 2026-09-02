package com.windrunner.server.tools.work;

import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.domain.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class SearchWorkItemsForBlockerTool {
    private final ProjectSearchService projectSearch;

    public LlmTool<Parameters> forProject(String projectId, String currentWorkItemId, Consumer<String> onWorkItemRead) {
        return new LlmTool<>(
                "search_work_items_for_blocker",
                "Search this project for WorkItems that may be relevant blockers. Use a focused query derived from the current WorkItem; only returned IDs may be proposed as blockers.",
                Parameters.class,
                parameters -> {
                    if (parameters == null || blank(parameters.query())) return List.of();
                    List<WorkItem> results = projectSearch.search(
                                    projectId, parameters.query(), AiReviewLimits.MAX_SEARCH_RESULTS)
                            .workItems().stream()
                            .filter(item -> !currentWorkItemId.equals(item.getId()))
                            .limit(AiReviewLimits.MAX_SEARCH_RESULTS)
                            .toList();
                    results.forEach(item -> onWorkItemRead.accept(item.getId()));
                    return results.stream().map(this::workItemSummary).toList();
                });
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private WorkItemSummary workItemSummary(WorkItem item) {
        return new WorkItemSummary(item.getId(), item.getParentWorkItemId(),
                AiReviewLimits.bounded(item.getTitle(), AiReviewLimits.MAX_TITLE_LENGTH),
                item.getType(), item.getStatus(), item.getDueDate(), item.getPriority());
    }

    public record Parameters(String query) { }

    public record WorkItemSummary(String id, String parentWorkItemId, String title, String type, String status,
                                  java.time.LocalDate dueDate, String priority) { }
}
