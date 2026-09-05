package com.windrunner.server.tools.identity;

import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.tools.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class FetchMembershipTool implements Tool<FetchMembershipTool.Parameters> {
    private final TeamService teams;
    private final ToolAuthorizationService authorization;
    private final TeamMemberRepository teamMembers;
    private final ProjectMemberRepository projectMembers;
    private final ProjectTeamRepository projectTeams;
    public String name() { return "fetch_membership"; }
    public String description() { return "Look up exactly one direct membership before proposing changes. For a team, provide teamId and userId and leave projectId empty (admin required). For a project, provide a selected-context projectId and exactly one non-empty subject ID: userId for a user membership or teamId for a team membership; leave the other subject ID empty (project owner required). Returns exists and current role; this is not a report of all inherited access."; }
    public Class<Parameters> parametersType() { return Parameters.class; }
    public boolean parallelSafe() { return true; }
    public Object execute(Parameters p, ToolExecutionContext context) {
        authorization.requireActor(context);
        if (p == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membership IDs required");
        String projectId = normalize(p.projectId());
        String teamId = normalize(p.teamId());
        String userId = normalize(p.userId());
        String role;
        if (projectId != null) {
            projectId = context.requireProjectId(projectId);
            authorization.requireProjectOwner(context, projectId);
            if ((userId == null) == (teamId == null)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply exactly one userId or teamId");
            role = userId != null
                    ? projectMembers.findByProjectIdAndUserId(projectId, userId).map(m -> m.getRole()).orElse(null)
                    : projectTeams.findByProjectIdAndTeamId(projectId, teamId).map(m -> m.getRole()).orElse(null);
        } else {
            authorization.requireAdmin(context);
            if (teamId == null || userId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teamId and userId required");
            teams.getTeam(teamId);
            role = teamMembers.findByTeamIdAndUserId(teamId, userId).map(m -> m.getRole()).orElse(null);
        }
        return new Result(projectId, teamId, userId, role != null, role);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    public record Parameters(String projectId, String teamId, String userId) { }
    public record Result(String projectId, String teamId, String userId, boolean exists, String role) { }
}
