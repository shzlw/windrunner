package com.windrunner.server.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiResponse", description = "Standard response envelope for all Windrunner API endpoints.")
public record ApiResponse<T>(
        @Schema(description = "The requested resource. Null when errors is non-empty.") T data,
        @Schema(description = "Errors reported for this request. Empty on success.") List<ApiError> errors,
        @Schema(description = "Request metadata. Present on paginated responses.") ApiMeta meta
) {
    public static <T> ApiResponse<T> success(T data) {
        return success(data, null);
    }

    public static <T> ApiResponse<T> success(T data, ApiMeta meta) {
        return new ApiResponse<>(data, List.of(), meta);
    }

    public static ApiResponse<Void> success() {
        return success(null, null);
    }

    public static <T> ApiResponse<List<T>> page(List<T> data, int page, int size, long totalItems, int totalPages) {
        return success(data, ApiMeta.page(null, page, size, totalItems, totalPages));
    }

    public static ApiResponse<Void> error(ApiError error, ApiMeta meta) {
        return new ApiResponse<>(null, List.of(error), meta);
    }
}
