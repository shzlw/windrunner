package com.windrunner.server.external.v1.dto;

import com.windrunner.server.project.domain.ProjectMember;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "ProjectMember", description = "A user's membership in a project.")
public record ExternalProjectMemberResponse(
        String projectId,
        String userId,
        String role,
        OffsetDateTime createdAt
) {
    public static ExternalProjectMemberResponse from(ProjectMember member) {
        return new ExternalProjectMemberResponse(
                member.getProjectId(),
                member.getUserId(),
                member.getRole(),
                member.getCreatedAt());
    }
}
