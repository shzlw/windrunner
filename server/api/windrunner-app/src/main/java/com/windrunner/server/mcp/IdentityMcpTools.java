package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.identity.FetchTeamDetailsTool;
import com.windrunner.server.tools.identity.FetchTeamMembersTool;
import com.windrunner.server.tools.identity.FetchTeamProjectsTool;
import com.windrunner.server.tools.identity.FetchTeamsTool;
import com.windrunner.server.tools.identity.FetchUserDetailsTool;
import com.windrunner.server.tools.identity.FetchUsersTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only MCP tools for discovering teams and users. These delegate to the
 * same bounded tools used by Ask AI so MCP clients follow the same
 * progressive-fetch contract.
 */
@Component
@RequiredArgsConstructor
public class IdentityMcpTools {

    private final McpAuthorization authorization;
    private final FetchTeamsTool teams;
    private final FetchTeamDetailsTool teamDetails;
    private final FetchTeamMembersTool teamMembers;
    private final FetchTeamProjectsTool teamProjects;
    private final FetchUsersTool users;
    private final FetchUserDetailsTool userDetails;

    @McpTool(
            name = "list_teams",
            description = "Find teams by name or description. Returns a small bounded candidate list; use get_team for one selected team's description and the paginated member/project tools for related records.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchTeamsTool.Result listTeams(String query, Integer limit) {
        authorization.requireScope(ApiKeyScopes.TEAMS_READ);
        return execute(teams, new FetchTeamsTool.Parameters(query, limit));
    }

    @McpTool(
            name = "get_team",
            description = "Get the name and description for one team. Start with list_teams when the team ID is not known.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchTeamDetailsTool.Result getTeam(String teamId) {
        authorization.requireScope(ApiKeyScopes.TEAMS_READ);
        return execute(teamDetails, new FetchTeamDetailsTool.Parameters(teamId));
    }

    @McpTool(
            name = "list_team_members",
            description = "List one bounded page of a team's members, including title, bio, and team role. Use limit and offset to fetch another page only when needed.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchTeamMembersTool.Result listTeamMembers(String teamId, Integer limit, Integer offset) {
        authorization.requireScope(ApiKeyScopes.TEAM_MEMBERS_READ);
        return execute(teamMembers, new FetchTeamMembersTool.Parameters(teamId, limit, offset));
    }

    @McpTool(
            name = "list_team_projects",
            description = "List one bounded page of projects linked to a team. Use limit and offset to fetch another page only when needed.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchTeamProjectsTool.Result listTeamProjects(String teamId, Integer limit, Integer offset) {
        authorization.requireScope(ApiKeyScopes.TEAM_PROJECTS_READ);
        return execute(teamProjects, new FetchTeamProjectsTool.Parameters(teamId, limit, offset));
    }

    @McpTool(
            name = "list_users",
            description = "Find active users by username, display name, email, title, or bio. Returns a small bounded candidate list; use get_user for the selected user's full profile.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FetchUsersTool.Result listUsers(String query, Integer limit) {
        authorization.requireScope(ApiKeyScopes.USERS_READ);
        return execute(users, new FetchUsersTool.Parameters(query, limit));
    }

    @McpTool(
            name = "get_user",
            description = "Get one active user's identity, title, and bio. Start with list_users when the user ID is not known.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public UserDetails getUser(String userId) {
        authorization.requireScope(ApiKeyScopes.USERS_READ);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        FetchUserDetailsTool.Result result = execute(userDetails,
                new FetchUserDetailsTool.Parameters(List.of(userId.trim())));
        FetchUserDetailsTool.ResultUser user = result.users().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserDetails(user.id(), user.username(), user.displayName(), user.email(), user.title(), user.bio());
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

    public record UserDetails(
            String id,
            String username,
            String displayName,
            String email,
            String title,
            String bio) {
    }
}
