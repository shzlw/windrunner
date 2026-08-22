package com.windrunner.server.external.v1.dto;

import com.windrunner.server.work.api.ProjectSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "SearchResult", description = "Hybrid full-text search matches across a project, grouped by entity type.")
public record ExternalSearchResultResponse(
        @Schema(description = "Matching work items plus any item containing matched entries or relationships.") List<ExternalWorkItemResponse> workItems,
        @Schema(description = "Entries whose body matched the query.") List<ExternalEntryResponse> entries,
        @Schema(description = "Relationships whose reason matched the query.") List<ExternalRelationshipResponse> relationships
) {
    public static ExternalSearchResultResponse from(ProjectSearchResult result) {
        return new ExternalSearchResultResponse(
                result.workItems().stream()
                        .map(item -> ExternalWorkItemResponse.from(item, List.of()))
                        .toList(),
                result.entries().stream().map(ExternalEntryResponse::from).toList(),
                result.relationships().stream().map(ExternalRelationshipResponse::from).toList());
    }
}
