package com.windrunner.server.project.api;

import java.util.List;

public record CreateProjectRequest(String name, List<String> ownerUserIds, List<String> ownerTeamIds) {
}
