package com.windrunner.server.audit;

public final class AuditActions {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String MOVE = "MOVE";
    public static final String AI_ACCEPT = "AI_ACCEPT";
    public static final String AI_REJECT = "AI_REJECT";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";

    private AuditActions() {
    }
}
