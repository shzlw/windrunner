package com.windrunner.server.external.v1.dto;

import com.windrunner.server.project.domain.Project;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "Project", description = "A Windrunner project.")
public record ExternalProjectResponse(
        String id,
        String name,
        String createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime archivedAt
) {
    public static ExternalProjectResponse from(Project project) {
        return new ExternalProjectResponse(
                project.getId(),
                project.getName(),
                project.getCreatedByUserId(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getArchivedAt());
    }
}
