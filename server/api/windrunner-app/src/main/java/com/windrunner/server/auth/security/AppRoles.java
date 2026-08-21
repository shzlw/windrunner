package com.windrunner.server.auth.security;

public final class AppRoles {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPERADMIN = "SUPERADMIN";

    private AppRoles() {
    }

    public static boolean isUser(String role) {
        return USER.equalsIgnoreCase(role);
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equalsIgnoreCase(role);
    }

    public static boolean isSuperAdmin(String role) {
        return SUPERADMIN.equalsIgnoreCase(role);
    }

    public static boolean isAdminLike(String role) {
        return isAdmin(role) || isSuperAdmin(role);
    }
}
