package com.windrunner.server.external.v1.dto;

import com.windrunner.server.team.domain.Team;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Team", description = "A team of users.")
public record ExternalTeamResponse(
        String id,
        String name,
        String description
) {

    public static ExternalTeamResponse from(Team team) {
        return new ExternalTeamResponse(team.getId(), team.getName(), team.getDescription());
    }
}
