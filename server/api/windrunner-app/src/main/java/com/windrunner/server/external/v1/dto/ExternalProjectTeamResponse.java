package com.windrunner.server.external.v1.dto;

import com.windrunner.server.team.domain.ProjectTeam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "ProjectTeam", description = "A team linked to a project, with the role that link grants.")
public record ExternalProjectTeamResponse(
        String projectId,
        String teamId,
        String role,
        OffsetDateTime createdAt
) {
    public static ExternalProjectTeamResponse from(ProjectTeam projectTeam) {
        return new ExternalProjectTeamResponse(
                projectTeam.getProjectId(),
                projectTeam.getTeamId(),
                projectTeam.getRole(),
                projectTeam.getCreatedAt());
    }
}
