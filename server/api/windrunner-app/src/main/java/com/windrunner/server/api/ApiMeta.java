package com.windrunner.server.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiMeta", description = "Request metadata. Pagination fields are present only on paginated responses.")
public record ApiMeta(
        @Schema(description = "Server-generated request identifier, useful for support.") String requestId,
        @Schema(description = "Current page number, zero-based.") Integer page,
        @Schema(description = "Page size used for this response.") Integer size,
        @Schema(description = "Total number of items across all pages.") Long totalItems,
        @Schema(description = "Total number of pages.") Integer totalPages
) {
    public static ApiMeta request(String requestId) {
        return new ApiMeta(requestId, null, null, null, null);
    }

    public static ApiMeta page(String requestId, int page, int size, long totalItems, int totalPages) {
        return new ApiMeta(requestId, page, size, totalItems, totalPages);
    }
}
