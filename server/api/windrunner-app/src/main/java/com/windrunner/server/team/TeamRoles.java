package com.windrunner.server.team;

public final class TeamRoles {

    public static final String TEAM_OWNER = "TEAM_OWNER";
    public static final String TEAM_MEMBER = "TEAM_MEMBER";

    private TeamRoles() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return TEAM_MEMBER;
        }
        String normalized = role.trim().toUpperCase();
        if (TEAM_OWNER.equals(normalized) || TEAM_MEMBER.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported team role: " + role);
    }
}
