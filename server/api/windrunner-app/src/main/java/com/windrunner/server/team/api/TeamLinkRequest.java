package com.windrunner.server.team.api;

public record TeamLinkRequest(String userId, String projectId, String teamId, String role) {
}
