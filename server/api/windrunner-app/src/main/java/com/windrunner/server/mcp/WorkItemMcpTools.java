package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.tools.work.FetchEntriesTool;
import com.windrunner.server.tools.work.FetchRelationshipsTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.AssignedWorkService;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.api.AssignedWorkItemView;
import com.windrunner.server.work.api.ProjectSearchResult;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkItemMcpTools {

    private final ProjectSearchService searchService;
    private final AssignedWorkService assignedWorkService;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;
    private final WorkItemRepository workItems;
    private final WorkItemAssigneeRepository assignees;
    private final FetchEntriesTool fetchEntries;
    private final FetchRelationshipsTool fetchRelationships;

    @McpTool(
            name = "search_work_items",
            description = "Full-text search (typo tolerant) across a project's work items, entries, and relationship reasons. Use this to find items before reading or modifying them.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public SearchResults searchWorkItems(String projectId, String query) {
        requireScope(ApiKeyScopes.WORK_ITEMS_READ);
        ToolExecutionContext context = requireProjectViewer(projectId);
        String normalizedProjectId = context.allowedProjectIds().getFirst();
        ProjectSearchResult result = searchService.search(normalizedProjectId, query == null ? "" : query, 20);
        return new SearchResults(
                result.workItems().stream().map(SearchWorkItem::from).toList(),
                result.entries().stream().map(SearchEntry::from).toList(),
                result.relationships().stream().map(SearchRelationship::from).toList());
    }

    @McpTool(
            name = "get_work_item",
            description = "Get one selected work item and the first bounded page of its entries and relationships. The response includes totals and hasMore flags; use list_entries or list_relationships for additional pages.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public WorkItemDetail getWorkItem(String projectId, String workItemId,
                                      Integer entryLimit, Integer relationshipLimit) {
        requireScopes(
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ);
        ToolExecutionContext context = requireProjectViewer(projectId);
        String normalizedProjectId = context.allowedProjectIds().getFirst();
        WorkItem item = workItems.findById(workItemId)
                .filter(i -> normalizedProjectId.equals(i.getProjectId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));

        FetchEntriesTool.Response entryPage = execute(fetchEntries,
                new FetchEntriesTool.Parameters(normalizedProjectId, workItemId, entryLimit, 0), context);
        FetchRelationshipsTool.Response relationshipPage = execute(fetchRelationships,
                new FetchRelationshipsTool.Parameters(normalizedProjectId, workItemId, relationshipLimit, 0), context);

        return new WorkItemDetail(
                item.getId(),
                item.getProjectId(),
                item.getParentWorkItemId(),
                item.getType(),
                item.getTitle(),
                item.getStatus(),
                item.getDueDate(),
                item.getPriority(),
                toStringOrNull(item.getCreatedAt()),
                toStringOrNull(item.getUpdatedAt()),
                assignees.findByWorkItemId(workItemId).stream()
                        .map(a -> new Assignee(a.getAssigneeType(), a.getAssigneeId()))
                        .toList(),
                entryPage.entries().stream().map(EntrySummary::from).toList(),
                entryPage.total(),
                entryPage.hasMore(),
                relationshipPage.relationships().stream()
                        .map(r -> RelationshipSummary.from(workItemId, r))
                        .toList(),
                relationshipPage.total(),
                relationshipPage.hasMore());
    }

    @McpTool(
            name = "list_my_work",
            description = "List one bounded page of work items assigned to the API key owner across visible projects. The response includes total and hasMore; use limit and offset for another page.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MyWorkPage listMyWork(Integer limit, Long offset) {
        requireScope(null);
        AppUser actor = McpActors.authenticatedActor();
        int normalizedLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 50));
        long normalizedOffset = offset == null ? 0 : Math.max(0, offset);
        List<MyWorkItem> items = assignedWorkService.listAssignedToUser(actor, normalizedLimit, normalizedOffset).stream()
                .map(view -> new MyWorkItem(view.projectId(), view.projectName(), view.workItemId(),
                        view.title(), view.type(), view.status(), view.dueDate(), view.priority()))
                .toList();
        long total = assignedWorkService.countAssignedToUser(actor);
        return new MyWorkPage(items, items.size(), total, normalizedLimit, normalizedOffset,
                normalizedOffset + items.size() < total);
    }

    /**
     * Per-tool scope enforcement. The authentication filter only proves key
     * validity; each tool declares the scope it actually needs.
     */
    private AppUser requireScope(String scope) {
        if (scope == null) {
            return McpActors.authenticatedActor();
        }
        return requireScopes(scope);
    }

    private AppUser requireScopes(String... scopes) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP API key is required");
        }
        return externalAccessService.requireScopes(attributes.getRequest(), scopes);
    }

    private ToolExecutionContext requireProjectViewer(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String normalizedProjectId = projectId.trim();
        AppUser actor = McpActors.authenticatedActor();
        projectAccessService.requireProjectRole(normalizedProjectId, actor, ProjectRoles.VIEWER);
        return new ToolExecutionContext(actor, null, List.of(normalizedProjectId));
    }

    @SuppressWarnings("unchecked")
    private <P, R> R execute(Tool<P> tool, P parameters, ToolExecutionContext context) {
        try {
            return (R) tool.execute(parameters, context);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("MCP read tool execution failed", exception);
        }
    }

    private static String toStringOrNull(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    @Schema(name = "SearchResults")
    public record SearchResults(
            List<SearchWorkItem> workItems,
            List<SearchEntry> entries,
            List<SearchRelationship> relationships) {
    }

    @Schema(name = "SearchWorkItem")
    public record SearchWorkItem(String id, String title, String type, String status) {
        static SearchWorkItem from(WorkItem item) {
            return new SearchWorkItem(item.getId(), item.getTitle(), item.getType(), item.getStatus());
        }
    }

    @Schema(name = "SearchEntry")
    public record SearchEntry(String id, String workItemId, String type, String body) {
        static SearchEntry from(Entry entry) {
            return new SearchEntry(entry.getId(), entry.getWorkItemId(), entry.getType(), entry.getBody());
        }
    }

    @Schema(name = "SearchRelationship")
    public record SearchRelationship(
            String id,
            String type,
            String fromEntityType,
            String fromEntityId,
            String toEntityType,
            String toEntityId,
            String reason) {
        static SearchRelationship from(Relationship relationship) {
            return new SearchRelationship(relationship.getId(), relationship.getType(),
                    relationship.getFromEntityType(), relationship.getFromEntityId(),
                    relationship.getToEntityType(), relationship.getToEntityId(), relationship.getReason());
        }
    }

    @Schema(name = "WorkItemDetail")
    public record WorkItemDetail(
            String id,
            String projectId,
            String parentWorkItemId,
            String type,
            String title,
            String status,
            LocalDate dueDate,
            String priority,
            String createdAt,
            String updatedAt,
            List<Assignee> assignees,
            List<EntrySummary> entries,
            long entriesTotal,
            boolean entriesHasMore,
            List<RelationshipSummary> relationships,
            long relationshipsTotal,
            boolean relationshipsHasMore) {
    }

    @Schema(name = "Assignee")
    public record Assignee(String assigneeType, String assigneeId) {
    }

    @Schema(name = "EntrySummary")
    public record EntrySummary(
            String id,
            String type,
            String authorUserId,
            String body,
            OffsetDateTime createdAt) {
        static EntrySummary from(Entry entry) {
            return new EntrySummary(entry.getId(), entry.getType(), entry.getAuthorUserId(),
                    entry.getBody(), entry.getCreatedAt());
        }
    }

    @Schema(name = "RelationshipSummary")
    public record RelationshipSummary(
            String id,
            String type,
            String direction,
            String otherEntityType,
            String otherEntityId,
            String reason) {
        static RelationshipSummary from(String workItemId, Relationship r) {
            boolean outgoing = workItemId.equals(r.getFromEntityId());
            return new RelationshipSummary(
                    r.getId(),
                    r.getType(),
                    outgoing ? "outgoing" : "incoming",
                    outgoing ? r.getToEntityType() : r.getFromEntityType(),
                    outgoing ? r.getToEntityId() : r.getFromEntityId(),
                    r.getReason());
        }
    }

    @Schema(name = "MyWorkItem")
    public record MyWorkItem(
            String projectId,
            String projectName,
            String workItemId,
            String title,
            String type,
            String status,
            LocalDate dueDate,
            String priority) {
    }

    @Schema(name = "MyWorkPage")
    public record MyWorkPage(
            List<MyWorkItem> items,
            int count,
            long total,
            int limit,
            long offset,
            boolean hasMore) {
    }
}
