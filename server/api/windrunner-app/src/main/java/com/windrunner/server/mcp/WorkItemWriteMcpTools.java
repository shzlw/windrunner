package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Write tools for MCP. Each tool enforces its own scope and requires the
 * project EDITOR role; actions are attributed to the API-key owner in the
 * audit log and notifications.
 */
@Component
@RequiredArgsConstructor
public class WorkItemWriteMcpTools {

    private final WorkItemService workItems;
    private final EntryService entries;
    private final RelationshipService relationships;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @McpTool(
            name = "add_entry",
            description = "Add an entry (comment, finding, answer, evidence) to a work item. Before calling, use search_work_items with a focused query to check whether the same substantive entry already exists. If a clear match exists, report it and do not add a duplicate; use an existing update capability when the user wants to change it.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public AddedEntry addEntry(String projectId, String workItemId, String body, String type) {
        AppUser actor = authorizeWrite(ApiKeyScopes.ENTRIES_WRITE, projectId);

        if (body == null || body.isBlank()) {
            throw bad("Entry body is required");
        }
        Entry entry = new Entry();
        entry.setWorkItemId(workItemId);
        entry.setType(type == null || type.isBlank() ? "COMMENT" : type.trim());
        entry.setBody(body);
        Entry created = entries.create(projectId, entry, actor.getId());
        return new AddedEntry(created.getId(), created.getWorkItemId(), created.getType(),
                "Entry added. View it on the work item to review.");
    }

    @McpTool(
            name = "update_work_item_status",
            description = "Change only the status of an existing work item (for example OPEN, IN_PROGRESS, DONE, BLOCKED). Read the work item first, verify its exact ID, and leave other fields untouched.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public StatusResult updateWorkItemStatus(String projectId, String workItemId, String status) {
        AppUser actor = authorizeWrite(ApiKeyScopes.WORK_ITEMS_WRITE, projectId);
        if (status == null || status.isBlank()) {
            throw bad("Status is required");
        }
        WorkItem updated = workItems.updateStatus(projectId, workItemId, status.trim(), actor.getId());
        return new StatusResult(updated.getId(), updated.getTitle(), updated.getStatus());
    }

    @McpTool(
            name = "create_work_item",
            description = "Create a new work item in a project: a NOTE, TASK, QUESTION, APPROVAL, REVIEW, or DECISION. Use NOTE for a simple title-only row. Before calling, use search_work_items with the intended title and inspect the relevant parent/type. If a clear existing match exists, report it and use an existing update capability instead of creating a duplicate. If matches are ambiguous, ask for clarification. Optionally place it under a parent work item.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public CreatedWorkItem createWorkItem(String projectId, String title, String type,
                                          String parentWorkItemId, String priority, String dueDate) {
        AppUser actor = authorizeWrite(ApiKeyScopes.WORK_ITEMS_WRITE, projectId);
        if (title == null || title.isBlank()) {
            throw bad("Title is required");
        }
        WorkItem item = new WorkItem();
        item.setTitle(title.trim());
        if (type != null && !type.isBlank()) item.setType(type.trim());
        if (parentWorkItemId != null && !parentWorkItemId.isBlank()) item.setParentWorkItemId(parentWorkItemId.trim());
        if (priority != null && !priority.isBlank()) item.setPriority(priority.trim());
        if (dueDate != null && !dueDate.isBlank()) {
            try {
                item.setDueDate(LocalDate.parse(dueDate.trim()));
            } catch (java.time.format.DateTimeParseException e) {
                throw bad("dueDate must be an ISO date, for example 2026-09-30");
            }
        }

        WorkItem created = workItems.create(projectId, item, List.of(), actor.getId());
        return new CreatedWorkItem(created.getId(), created.getProjectId(), created.getParentWorkItemId(),
                created.getType(), created.getTitle(), created.getStatus(),
                "Created. Assignees should be set by a human in the workspace.");
    }

    @McpTool(
            name = "link_relationship",
            description = "Create a typed relationship between two items, for example BLOCKED_BY or DEPENDS_ON between two work items. Before calling, read/search the relevant relationships and compare both endpoints and the type. If an exact relationship exists, report it and do not create a duplicate; use an existing update capability when the user wants to change its reason. If the endpoints or type are ambiguous, ask for clarification.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public CreatedRelationship linkRelationship(String projectId, String type,
                                                String fromEntityId, String toEntityId, String reason) {
        AppUser actor = authorizeWrite(ApiKeyScopes.RELATIONSHIPS_WRITE, projectId);
        if (type == null || fromEntityId == null || toEntityId == null) {
            throw bad("Type, fromEntityId and toEntityId are required");
        }
        Relationship relationship = new Relationship();
        relationship.setFromEntityType("WORK_ITEM");
        relationship.setFromEntityId(fromEntityId);
        relationship.setToEntityType("WORK_ITEM");
        relationship.setToEntityId(toEntityId);
        relationship.setType(type);
        relationship.setReason(reason);

        Relationship created = relationships.create(projectId, relationship, actor.getId());
        return new CreatedRelationship(created.getId(), created.getType(),
                created.getFromEntityId(), created.getToEntityId(), created.getReason());
    }

    /** Enforces the tool's scope and returns the acting user. */
    private AppUser requireScope(String scope) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP API key is required");
        }
        return externalAccessService.requireScope(attributes.getRequest(), scope);
    }

    private AppUser authorizeWrite(String scope, String projectId) {
        AppUser actor = requireScope(scope);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return actor;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Schema(name = "AddedEntry")
    public record AddedEntry(String id, String workItemId, String type, String message) {
    }

    @Schema(name = "StatusResult")
    public record StatusResult(String id, String title, String status) {
    }

    @Schema(name = "CreatedWorkItem")
    public record CreatedWorkItem(
            String id,
            String projectId,
            String parentWorkItemId,
            String type,
            String title,
            String status,
            String message) {
    }

    @Schema(name = "CreatedRelationship")
    public record CreatedRelationship(
            String id,
            String type,
            String fromEntityId,
            String toEntityId,
            String reason) {
    }
}
