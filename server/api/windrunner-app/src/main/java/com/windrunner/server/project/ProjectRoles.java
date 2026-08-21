package com.windrunner.server.project;

public final class ProjectRoles {

    public static final String OWNER = "OWNER";
    public static final String EDITOR = "EDITOR";
    public static final String VIEWER = "VIEWER";

    private ProjectRoles() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return VIEWER;
        }
        String normalized = role.trim().toUpperCase();
        if (OWNER.equals(normalized) || EDITOR.equals(normalized) || VIEWER.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported project role: " + role);
    }
}
