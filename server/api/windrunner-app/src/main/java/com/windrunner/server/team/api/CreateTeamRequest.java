package com.windrunner.server.team.api;

import java.util.List;

public record CreateTeamRequest(
        String name,
        String description,
        List<String> ownerUserIds
) {
    public CreateTeamRequest(String name, List<String> ownerUserIds) {
        this(name, null, ownerUserIds);
    }
}
