package com.windrunner.server.tools;

public interface Tool<T> {

    String name();

    String description();

    Class<T> parametersType();

    Object execute(T parameters, ToolExecutionContext context) throws Exception;
}
