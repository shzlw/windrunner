package com.windrunner.server.tools.identity;

import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class FetchTeamProjectsTool implements Tool<FetchTeamProjectsTool.Parameters> {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final TeamRepository teamRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final ProjectRepository projectRepository;

    @Override
    public String name() {
        return "fetch_team_projects";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-team-projects-tool.md");
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
        Integer requestedLimit = parameters == null ? null : parameters.limit();
        int limit = requestedLimit == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        List<ProjectTeam> links = projectTeamRepository.findByTeamId(teamId).stream().limit(limit).toList();
        Map<String, Project> projectsById = new LinkedHashMap<>();
        projectRepository.findAllById(links.stream().map(ProjectTeam::getProjectId).toList())
                .forEach(project -> projectsById.put(project.getId(), project));
        List<LinkedProject> projects = links.stream()
                .map(link -> {
                    Project project = projectsById.get(link.getProjectId());
                    return new LinkedProject(link.getProjectId(), project == null ? null : project.getName(), link.getRole());
                })
                .toList();
        return new Result(team.getId(), team.getName(), projects, projects.size(), limit);
    }

    public record Parameters(String teamId, Integer limit) { }

    public record Result(String teamId, String teamName, List<LinkedProject> projects, int count, int limit) { }

    public record LinkedProject(String projectId, String name, String role) { }
}
