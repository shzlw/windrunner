package com.windrunner.server.llm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.regex.Pattern;

public record LlmTool<T>(
        String name,
        String description,
        Class<T> parametersType,
        LlmToolHandler<T> handler
) {

    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public LlmTool {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "LLM tool name must contain 1-64 letters, numbers, underscores, or hyphens"
            );
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("LLM tool description is required");
        }
        Objects.requireNonNull(parametersType, "LLM tool parameters type is required");
        Objects.requireNonNull(handler, "LLM tool handler is required");
    }

    public Object execute(String arguments, ObjectMapper objectMapper) throws Exception {
        T parsedArguments;
        try {
            parsedArguments = objectMapper.readValue(arguments, parametersType);
        } catch (JacksonException exception) {
            throw new LlmException(
                    "LLM returned invalid arguments for tool " + name,
                    exception
            );
        }
        return handler.execute(parsedArguments);
    }
}
