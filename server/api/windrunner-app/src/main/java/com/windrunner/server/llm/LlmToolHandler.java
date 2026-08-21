package com.windrunner.server.llm;

@FunctionalInterface
public interface LlmToolHandler<T> {

    Object execute(T arguments) throws Exception;
}
