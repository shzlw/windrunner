package com.windrunner.server.tools.identity;

import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
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
public class FetchTeamMembersTool implements Tool<FetchTeamMembersTool.Parameters> {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AppUserRepository userRepository;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_team_members";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-team-members-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String teamId = parameters == null || parameters.teamId() == null ? "" : parameters.teamId().trim();
        if (teamId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team id is required");
        }
        authorization.requireContext(context);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        Integer requestedLimit = parameters == null ? null : parameters.limit();
        int limit = requestedLimit == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        long offset = parameters == null || parameters.offset() == null ? 0 : Math.max(0, parameters.offset());
        List<TeamMember> memberships = teamMemberRepository.findPageByTeamId(teamId, limit, offset);
        Map<String, AppUser> usersById = new LinkedHashMap<>();
        if (!memberships.isEmpty()) {
            userRepository.findActiveUsersByIds(memberships.stream().map(TeamMember::getUserId).toList())
                    .forEach(user -> usersById.put(user.getId(), user));
        }
        List<Member> members = memberships.stream()
                .map(member -> {
                    AppUser user = usersById.get(member.getUserId());
                    return new Member(member.getUserId(), user == null ? null : user.getUsername(), user == null ? null : user.getDisplayName(), user == null ? null : user.getTitle(), user == null ? null : user.getBio(), member.getRole());
                })
                .toList();
        long total = teamMemberRepository.countByTeamId(teamId);
        return new Result(team.getId(), team.getName(), members, members.size(), total, limit, offset, offset + members.size() < total);
    }

    public record Parameters(String teamId, Integer limit, Integer offset) { }

    public record Result(String teamId, String teamName, List<Member> members, int count, long total, int limit, long offset, boolean hasMore) { }

    public record Member(String userId, String username, String displayName, String title, String bio, String role) { }
}
