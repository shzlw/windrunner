package com.windrunner.server.tools.identity;

import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchProjectAssigneesTool implements Tool<FetchProjectAssigneesTool.Parameters> {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_TEXT_LENGTH = 4000;

    private final AppUserRepository users;
    private final TeamRepository teams;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_project_assignees";
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-project-assignees-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(
                context, parameters == null ? null : parameters.projectId());
        String query = parameters == null || parameters.query() == null || parameters.query().isBlank()
                ? null
                : parameters.query().trim();
        if (query != null && query.length() > MAX_QUERY_LENGTH) query = query.substring(0, MAX_QUERY_LENGTH);
        int limit = parameters == null || parameters.limit() == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(parameters.limit(), MAX_LIMIT));
        List<UserCandidate> userCandidates = users.findAssignableUsersForProject(projectId, query, limit).stream()
                .map(UserCandidate::from)
                .toList();
        List<TeamCandidate> teamCandidates = teams.findAssignableTeamsForProject(projectId, query, limit).stream()
                .map(TeamCandidate::from)
                .toList();
        return new Result(projectId, userCandidates, teamCandidates, userCandidates.size(), teamCandidates.size(), limit);
    }

    public record Parameters(String projectId, String query, Integer limit) {
    }

    public record Result(String projectId, List<UserCandidate> users, List<TeamCandidate> teams,
                         int userCount, int teamCount, int limit) {
    }

    public record UserCandidate(String id, String username, String displayName, String email) {
        static UserCandidate from(AppUser user) {
            return new UserCandidate(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail());
        }
    }

    public record TeamCandidate(String id, String name, String description) {
        static TeamCandidate from(Team team) {
            return new TeamCandidate(team.getId(), team.getName(), truncate(team.getDescription()));
        }
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_TEXT_LENGTH
                ? value.substring(0, MAX_TEXT_LENGTH) + "…"
                : value;
    }
}
