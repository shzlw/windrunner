package com.windrunner.server.tools.identity;

import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class FetchTeamDetailsTool implements Tool<FetchTeamDetailsTool.Parameters> {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final AppUserRepository userRepository;
    private final ProjectRepository projectRepository;

    @Override
    public String name() {
        return "fetch_team_details";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-team-details-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters) {
        String teamId = parameters == null || parameters.teamId() == null ? "" : parameters.teamId().trim();
        if (teamId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team id is required");
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(teamId);
        Map<String, AppUser> usersById = new LinkedHashMap<>();
        for (AppUser user : userRepository.findAllById(teamMembers.stream().map(TeamMember::getUserId).toList())) {
            usersById.put(user.getId(), user);
        }
        List<Member> members = teamMembers.stream()
                .map(member -> {
                    AppUser user = usersById.get(member.getUserId());
                    return new Member(member.getUserId(), user == null ? null : user.getUsername(), user == null ? null : user.getDisplayName(), user == null ? null : user.getTitle(), user == null ? null : user.getBio(), member.getRole());
                })
                .toList();

        List<ProjectTeam> projectLinks = projectTeamRepository.findByTeamId(teamId);
        Map<String, Project> projectsById = new LinkedHashMap<>();
        for (Project project : projectRepository.findAllById(projectLinks.stream().map(ProjectTeam::getProjectId).toList())) {
            projectsById.put(project.getId(), project);
        }
        List<LinkedProject> projects = projectLinks.stream()
                .map(link -> {
                    Project project = projectsById.get(link.getProjectId());
                    return new LinkedProject(link.getProjectId(), project == null ? null : project.getName(), link.getRole());
                })
                .toList();

        return new Result(team.getId(), team.getName(), team.getDescription(), members, projects);
    }

    public record Parameters(String teamId) { }

    public record Result(String id, String name, String description, List<Member> members, List<LinkedProject> projects) { }

    public record Member(String userId, String username, String displayName, String title, String bio, String role) { }

    public record LinkedProject(String projectId, String name, String role) { }
}
