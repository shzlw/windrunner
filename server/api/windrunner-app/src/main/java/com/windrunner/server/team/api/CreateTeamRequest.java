package com.windrunner.server.team.api;

import java.util.List;

public record CreateTeamRequest(
        String name,
        List<String> ownerUserIds
) {
}
