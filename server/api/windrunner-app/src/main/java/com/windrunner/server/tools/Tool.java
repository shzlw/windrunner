package com.windrunner.server.tools;

public interface Tool<T> {

    String name();

    String description();

    Class<T> parametersType();

    Object execute(T parameters, ToolExecutionContext context) throws Exception;

    /**
     * Whether independent calls to this tool may run concurrently.
     * Stateful or mutating tools must keep the default.
     */
    default boolean parallelSafe() {
        return false;
    }
}
