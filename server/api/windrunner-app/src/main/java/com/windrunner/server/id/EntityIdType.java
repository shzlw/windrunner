package com.windrunner.server.id;

public enum EntityIdType {
    API_KEY("akey"),
    AUDIT_LOG("audt"),
    AUTH_SESSION("sess"),
    AGENT_MESSAGE_REQUEST("amrq"),
    CHAT_MESSAGE("cmsg"),
    CHAT_SESSION("cses"),
    CHAT_SESSION_CONTEXT("csct"),
    LLM_USAGE("llmu"),
    PROJECT("proj"),
    TEAM("team"),
    TEAM_JOIN_REQUEST("tjrq"),
    USER("user"),
    USER_SETTING("uset"),
    WORK_ITEM("witm"),
    WORK_ITEM_ASSIGNEE("wias"),
    ENTRY("entr"),
    RELATIONSHIP("rela"),
    WORKSPACE_CHANGE_PROPOSAL("wcpr"),
    WORKSPACE_CHANGE("wchg"),
    WORK_ITEM_SUBSCRIPTION("wisu"),
    USER_NOTIFICATION("unot");

    private final String prefix;

    EntityIdType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
