package com.windrunner.server.external.v1.dto;

import com.windrunner.server.team.domain.Team;

public record ExternalTeamResponse(
        String id,
        String name
) {

    public static ExternalTeamResponse from(Team team) {
        return new ExternalTeamResponse(team.getId(), team.getName());
    }
}
