package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.work.FetchEntriesTool;
import com.windrunner.server.tools.work.FetchProjectBlockersTool;
import com.windrunner.server.tools.work.FetchProjectSummaryTool;
import com.windrunner.server.tools.work.FetchRelationshipsTool;
import com.windrunner.server.tools.work.FetchWorkItemsTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.stereotype.Component;

/**
 * Read-only MCP tools for project content. Each page is authorized before the
 * existing progressive fetch tool runs, keeping MCP and Ask AI bounded reads
 * on the same repository/service paths.
 */
@Component
@RequiredArgsConstructor
public class ProjectReadMcpTools {

    private final McpAuthorization authorization;
    private final FetchWorkItemsTool workItems;
    private final FetchEntriesTool entries;
    private final FetchRelationshipsTool relationships;
    private final FetchProjectBlockersTool blockers;
    private final FetchProjectSummaryTool summary;

    @McpTool(
            name = "list_work_items",
            description = "List one bounded page of a project's work items, optionally filtered by query. The response includes total and hasMore; fetch another page only when needed, then use get_work_item for one selected item.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchWorkItemsTool.Response listWorkItems(String projectId, String query, Integer limit, Integer offset) {
        String normalizedProjectId = authorizeProject(projectId, ApiKeyScopes.WORK_ITEMS_READ);
        return execute(workItems, new FetchWorkItemsTool.Parameters(normalizedProjectId, query, limit, offset));
    }

    @McpTool(
            name = "list_entries",
            description = "List one bounded page of a project's entries, optionally for one work item. The response includes total and hasMore; use a workItemId when investigating a specific item.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchEntriesTool.Response listEntries(String projectId, String workItemId, Integer limit, Integer offset) {
        String normalizedProjectId = authorizeProject(projectId, ApiKeyScopes.ENTRIES_READ);
        return execute(entries, new FetchEntriesTool.Parameters(normalizedProjectId, workItemId, limit, offset));
    }

    @McpTool(
            name = "list_relationships",
            description = "List one bounded page of a project's relationships, optionally for one entity. The response includes total and hasMore; use an entityId when investigating a specific item.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchRelationshipsTool.Response listRelationships(String projectId, String entityId, Integer limit, Integer offset) {
        String normalizedProjectId = authorizeProject(projectId, ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(relationships, new FetchRelationshipsTool.Parameters(normalizedProjectId, entityId, limit, offset));
    }

    @McpTool(
            name = "list_project_blockers",
            description = "List one bounded page of work-item blockers for a project. Blockers are aggregated server-side and the response includes total and hasMore; fetch another page only when needed.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchProjectBlockersTool.Response listProjectBlockers(String projectId, Integer limit, Integer offset) {
        String normalizedProjectId = authorizeProject(projectId,
                ApiKeyScopes.WORK_ITEMS_READ, ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(blockers, new FetchProjectBlockersTool.Parameters(normalizedProjectId, limit, offset));
    }

    @McpTool(
            name = "get_project_summary",
            description = "Return server-side project totals and distributions for status, type, priority, entries, relationships, due dates, and assignees. Use this aggregate before fetching individual records; it does not dump every work item or entry.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchProjectSummaryTool.Response getProjectSummary(String projectId) {
        String normalizedProjectId = authorizeProject(projectId,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(summary, new FetchProjectSummaryTool.Parameters(normalizedProjectId));
    }

    private String authorizeProject(String projectId, String... scopes) {
        String normalizedProjectId = authorization.requireProjectId(projectId);
        authorization.requireProjectViewer(normalizedProjectId, scopes);
        return normalizedProjectId;
    }

    @SuppressWarnings("unchecked")
    private <P, R> R execute(Tool<P> tool, P parameters) {
        try {
            return (R) tool.execute(parameters);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("MCP read tool execution failed", exception);
        }
    }
}
