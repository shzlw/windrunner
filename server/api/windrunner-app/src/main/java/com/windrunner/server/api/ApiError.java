package com.windrunner.server.api;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "A single error reported for a request.")
public record ApiError(
        @Schema(description = "Stable machine-readable error code.", example = "NOT_FOUND") String code,
        @Schema(description = "Human-readable error message.") String message,
        @Schema(description = "Request field the error relates to, when applicable.") String field,
        @Schema(description = "Additional structured details for this error.") Map<String, Object> details
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, null);
    }
}
