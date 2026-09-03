package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.tools.work.FetchEntriesTool;
import com.windrunner.server.tools.work.FetchProjectBlockersTool;
import com.windrunner.server.tools.work.FetchProjectSummaryTool;
import com.windrunner.server.tools.work.FetchRelationshipsTool;
import com.windrunner.server.tools.work.FetchWorkItemsTool;
import com.windrunner.server.tools.work.FindRelationshipsExactTool;
import com.windrunner.server.tools.work.SearchEntriesTool;
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
    private final SearchEntriesTool searchEntries;
    private final FindRelationshipsExactTool exactRelationships;

    @McpTool(
            name = "list_work_items",
            description = "List one bounded page of a project's work items, optionally filtered by title query, exact parentWorkItemId, or exact type. Use PROJECT_ROOT for top-level items. The response includes total and hasMore; fetch another page only when needed, then use get_work_item for one selected item.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchWorkItemsTool.Response listWorkItems(String projectId, String query,
                                                     String parentWorkItemId, String type,
                                                     Integer limit, Integer offset) {
        AuthorizedProject authorizedProject = authorizeProject(projectId, ApiKeyScopes.WORK_ITEMS_READ);
        return execute(workItems, new FetchWorkItemsTool.Parameters(
                authorizedProject.projectId(), query, parentWorkItemId, type, limit, offset), authorizedProject.context());
    }

    public FetchWorkItemsTool.Response listWorkItems(String projectId, String query, Integer limit, Integer offset) {
        return listWorkItems(projectId, query, null, null, limit, offset);
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
        AuthorizedProject authorizedProject = authorizeProject(projectId, ApiKeyScopes.ENTRIES_READ);
        return execute(entries, new FetchEntriesTool.Parameters(
                authorizedProject.projectId(), workItemId, limit, offset), authorizedProject.context());
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
        AuthorizedProject authorizedProject = authorizeProject(projectId, ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(relationships, new FetchRelationshipsTool.Parameters(
                authorizedProject.projectId(), entityId, limit, offset), authorizedProject.context());
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
        AuthorizedProject authorizedProject = authorizeProject(projectId,
                ApiKeyScopes.WORK_ITEMS_READ, ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(blockers, new FetchProjectBlockersTool.Parameters(
                authorizedProject.projectId(), limit, offset), authorizedProject.context());
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
        AuthorizedProject authorizedProject = authorizeProject(projectId,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(summary, new FetchProjectSummaryTool.Parameters(
                authorizedProject.projectId()), authorizedProject.context());
    }

    @McpTool(
            name = "search_entries",
            description = "Search bounded entry candidates in a project, optionally restricted to one work item. Set exact=true with the full body and workItemId to check for an exact duplicate; otherwise use ranked full-text candidates.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public SearchEntriesTool.Response searchEntries(String projectId, String workItemId, String query,
                                                    Boolean exact, Integer limit, Integer offset) {
        AuthorizedProject authorizedProject = authorizeProject(projectId, ApiKeyScopes.ENTRIES_READ);
        return execute(searchEntries, new SearchEntriesTool.Parameters(
                authorizedProject.projectId(), workItemId, query, exact, limit, offset), authorizedProject.context());
    }

    @McpTool(
            name = "find_relationships_exact",
            description = "Find relationships matching one project's exact from endpoint, to endpoint, and relationship type. Use this targeted check before proposing a new relationship.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FindRelationshipsExactTool.Response findRelationshipsExact(String projectId, String fromType, String fromId,
                                                                      String toType, String toId, String relationshipType) {
        AuthorizedProject authorizedProject = authorizeProject(projectId, ApiKeyScopes.RELATIONSHIPS_READ);
        return execute(exactRelationships, new FindRelationshipsExactTool.Parameters(
                authorizedProject.projectId(), fromType, fromId, toType, toId, relationshipType), authorizedProject.context());
    }

    private AuthorizedProject authorizeProject(String projectId, String... scopes) {
        String normalizedProjectId = authorization.requireProjectId(projectId);
        ToolExecutionContext context = authorization.toolContext(
                authorization.requireProjectViewer(normalizedProjectId, scopes), normalizedProjectId);
        return new AuthorizedProject(normalizedProjectId, context);
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

    private record AuthorizedProject(String projectId, ToolExecutionContext context) {
    }
}
