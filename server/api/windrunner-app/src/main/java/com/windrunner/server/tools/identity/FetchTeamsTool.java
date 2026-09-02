package com.windrunner.server.tools.identity;

import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class FetchTeamsTool implements Tool<FetchTeamsTool.Parameters> {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String PROMPT_NAME = "fetch-teams-tool.md";

    private final TeamRepository teamRepository;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_teams";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt(PROMPT_NAME);
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        int limit = parameters == null || parameters.limit() == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(parameters.limit(), MAX_LIMIT));
        String query = parameters == null ? null : normalizeQuery(parameters.query());
        authorization.requireContext(context);
        List<ResultTeam> teams = teamRepository.findAssignableTeams(query, limit).stream()
                .map(ResultTeam::from)
                .toList();
        return new Result(teams, teams.size(), limit);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    public record Parameters(String query, Integer limit) {
    }

    public record Result(List<ResultTeam> teams, int count, int limit) {
    }

    public record ResultTeam(String id, String name, String description) {

        static ResultTeam from(Team team) {
            return new ResultTeam(team.getId(), team.getName(), team.getDescription());
        }
    }
}
