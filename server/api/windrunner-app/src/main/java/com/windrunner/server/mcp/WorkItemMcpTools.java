package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.AssignedWorkService;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.api.AssignedWorkItemView;
import com.windrunner.server.work.api.ProjectSearchResult;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
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
    private final EntryRepository entries;
    private final RelationshipRepository relationships;

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
        requireProjectViewer(projectId);
        ProjectSearchResult result = searchService.search(projectId, query == null ? "" : query, 20);
        return new SearchResults(
                result.workItems().stream().map(SearchWorkItem::from).toList(),
                result.entries().stream().map(SearchEntry::from).toList(),
                result.relationships().stream().map(SearchRelationship::from).toList());
    }

    @McpTool(
            name = "get_work_item",
            description = "Get the full picture of one work item: fields, assignees, entries, and relationships (blockers, dependencies). Prefer this over guessing about an item's state.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public WorkItemDetail getWorkItem(String projectId, String workItemId) {
        requireScope(ApiKeyScopes.WORK_ITEMS_READ);
        requireProjectViewer(projectId);
        WorkItem item = workItems.findById(workItemId)
                .filter(i -> projectId.equals(i.getProjectId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));

        List<Entry> itemEntries = entries.findByWorkItemId(workItemId);
        List<Relationship> itemRelationships = relationships.findByProjectId(projectId).stream()
                .filter(r -> workItemId.equals(r.getFromEntityId()) || workItemId.equals(r.getToEntityId()))
                .toList();

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
                itemEntries.stream().map(EntrySummary::from).toList(),
                itemRelationships.stream()
                        .map(r -> RelationshipSummary.from(workItemId, r))
                        .toList());
    }

    @McpTool(
            name = "list_my_work",
            description = "List work items assigned to the API key owner across all visible projects, ordered by due date. Use for planning and status questions.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public List<MyWorkItem> listMyWork() {
        requireScope(null);
        AppUser actor = McpActors.authenticatedActor();
        return assignedWorkService.listAssignedToUser(actor, 0, 50).stream()
                .map(view -> new MyWorkItem(view.projectId(), view.projectName(), view.workItemId(),
                        view.title(), view.type(), view.status(), view.dueDate(), view.priority()))
                .toList();
    }

    /**
     * Per-tool scope enforcement. The authentication filter only proves key
     * validity; each tool declares the scope it actually needs.
     */
    private AppUser requireScope(String scope) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP API key is required");
        }
        if (scope == null) {
            return McpActors.authenticatedActor();
        }
        return externalAccessService.requireScope(attributes.getRequest(), scope);
    }

    private void requireProjectViewer(String projectId) {
        AppUser actor = McpActors.authenticatedActor();
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
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
            List<RelationshipSummary> relationships) {
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
}
