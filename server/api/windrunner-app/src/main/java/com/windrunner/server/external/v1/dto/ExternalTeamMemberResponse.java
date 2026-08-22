package com.windrunner.server.external.v1.dto;

import com.windrunner.server.team.domain.TeamMember;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "TeamMember", description = "A user's membership in a team.")
public record ExternalTeamMemberResponse(
        String teamId,
        String userId,
        String role,
        OffsetDateTime createdAt
) {
    public static ExternalTeamMemberResponse from(TeamMember member) {
        return new ExternalTeamMemberResponse(
                member.getTeamId(),
                member.getUserId(),
                member.getRole(),
                member.getCreatedAt());
    }
}
