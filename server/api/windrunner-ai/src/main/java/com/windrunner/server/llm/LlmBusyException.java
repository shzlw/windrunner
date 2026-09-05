package com.windrunner.server.llm;

public class LlmBusyException extends LlmException {

    public LlmBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
