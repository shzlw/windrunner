package com.windrunner.server.apikey;

import java.util.List;
import java.util.Set;

public final class ApiKeyScopes {

    public static final String TEAMS_READ = "teams:read";
    public static final String TEAMS_WRITE = "teams:write";
    public static final String TEAM_MEMBERS_READ = "team_members:read";
    public static final String TEAM_MEMBERS_WRITE = "team_members:write";
    public static final String TEAM_PROJECTS_READ = "team_projects:read";
    public static final String TEAM_PROJECTS_WRITE = "team_projects:write";
    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String PROJECTS_READ = "projects:read";
    public static final String PROJECTS_WRITE = "projects:write";
    public static final String PROJECT_ACCESS_READ = "project_access:read";
    public static final String PROJECT_ACCESS_WRITE = "project_access:write";
    public static final String WORK_ITEMS_READ = "work_items:read";
    public static final String WORK_ITEMS_WRITE = "work_items:write";
    public static final String ENTRIES_READ = "entries:read";
    public static final String ENTRIES_WRITE = "entries:write";
    public static final String RELATIONSHIPS_READ = "relationships:read";
    public static final String RELATIONSHIPS_WRITE = "relationships:write";
    public static final String AUDIT_LOGS_READ = "audit_logs:read";

    public static final List<String> ORDERED_SCOPES = List.of(
            TEAMS_READ,
            TEAMS_WRITE,
            TEAM_MEMBERS_READ,
            TEAM_MEMBERS_WRITE,
            TEAM_PROJECTS_READ,
            TEAM_PROJECTS_WRITE,
            USERS_READ,
            USERS_WRITE,
            PROJECTS_READ,
            PROJECTS_WRITE,
            PROJECT_ACCESS_READ,
            PROJECT_ACCESS_WRITE,
            WORK_ITEMS_READ,
            WORK_ITEMS_WRITE,
            ENTRIES_READ,
            ENTRIES_WRITE,
            RELATIONSHIPS_READ,
            RELATIONSHIPS_WRITE,
            AUDIT_LOGS_READ
    );

    private static final Set<String> ALLOWED_SCOPES = Set.copyOf(ORDERED_SCOPES);

    private ApiKeyScopes() {
    }

    public static boolean isAllowed(String scope) {
        return ALLOWED_SCOPES.contains(scope);
    }
}
