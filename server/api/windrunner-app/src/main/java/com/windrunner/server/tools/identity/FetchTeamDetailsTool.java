package com.windrunner.server.tools.identity;

import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;


@RequiredArgsConstructor
@Component
public class FetchTeamDetailsTool implements Tool<FetchTeamDetailsTool.Parameters> {

    private final TeamRepository teamRepository;
    @Override
    public String name() {
        return "fetch_team_details";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-team-details-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters) {
        String teamId = parameters == null || parameters.teamId() == null ? "" : parameters.teamId().trim();
        if (teamId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team id is required");
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        return new Result(team.getId(), team.getName(), team.getDescription());
    }

    public record Parameters(String teamId) { }

    public record Result(String id, String name, String description) { }
}
