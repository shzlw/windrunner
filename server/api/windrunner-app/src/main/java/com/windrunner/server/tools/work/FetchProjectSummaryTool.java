package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchProjectSummaryTool implements Tool<FetchProjectSummaryTool.Parameters> {

    private final WorkItemRepository workItems;
    private final WorkItemAssigneeRepository assignees;
    private final EntryRepository entries;
    private final RelationshipRepository relationships;

    @Override
    public String name() {
        return "fetch_project_summary";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-project-summary-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters) {
        requireProjectId(parameters);
        String projectId = parameters.projectId().trim();
        return new Response(
                projectId,
                new Totals(
                        workItems.countAllByProjectId(projectId),
                        entries.countAllByProjectId(projectId),
                        relationships.countAllByProjectId(projectId),
                        relationships.countWorkItemBlockers(projectId),
                        relationships.countBlockedWorkItems(projectId)),
                new Distributions(
                        toWorkItemCounts(workItems.countByStatusForProject(projectId)),
                        toWorkItemCounts(workItems.countByTypeForProject(projectId)),
                        toWorkItemCounts(workItems.countByPriorityForProject(projectId)),
                        toEntryCounts(entries.countByTypeForProject(projectId)),
                        toRelationshipCounts(relationships.countByTypeForProject(projectId)),
                        workItems.summarizeDueDates(projectId),
                        assignees.countByProjectId(projectId).stream().map(row -> new AssigneeCount(
                                row.assigneeType(), row.assigneeId(), row.assigneeLabel(), row.count())).toList()));
    }

    private void requireProjectId(Parameters parameters) {
        if (parameters == null || parameters.projectId() == null || parameters.projectId().isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
    }

    private List<Count> toWorkItemCounts(List<WorkItemRepository.DistributionRow> rows) {
        return rows.stream().map(row -> new Count(row.value(), row.count())).toList();
    }

    private List<Count> toEntryCounts(List<EntryRepository.DistributionRow> rows) {
        return rows.stream().map(row -> new Count(row.value(), row.count())).toList();
    }

    private List<Count> toRelationshipCounts(List<RelationshipRepository.DistributionRow> rows) {
        return rows.stream().map(row -> new Count(row.value(), row.count())).toList();
    }

    public record Parameters(String projectId) {
    }

    public record Response(String projectId, Totals totals, Distributions distributions) {
    }

    public record Totals(long workItems, long entries, long relationships, long blockers, long blockedWorkItems) {
    }

    public record Distributions(
            List<Count> workItemsByStatus,
            List<Count> workItemsByType,
            List<Count> workItemsByPriority,
            List<Count> entriesByType,
            List<Count> relationshipsByType,
            WorkItemRepository.DueDateSummary dueDates,
            List<AssigneeCount> workItemsByAssignee
    ) {
    }

    public record Count(String value, long count) {
    }

    public record AssigneeCount(String assigneeType, String assigneeId, String assigneeLabel, long count) {
    }
}
