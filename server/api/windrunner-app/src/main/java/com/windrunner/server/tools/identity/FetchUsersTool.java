package com.windrunner.server.tools.identity;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class FetchUsersTool implements Tool<FetchUsersTool.Parameters> {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String PROMPT_NAME = "fetch-users-tool.md";

    private final AppUserRepository appUserRepository;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_users";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt(PROMPT_NAME);
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        int limit = parameters == null || parameters.limit() == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(parameters.limit(), MAX_LIMIT));
        String query = parameters == null ? null : normalizeQuery(parameters.query());
        authorization.requireContext(context);
        List<ResultUser> users = appUserRepository.findActiveAssignableUsers(query, limit).stream()
                .map(ResultUser::from)
                .toList();
        return new Result(users, users.size(), limit);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    public record Parameters(String query, Integer limit) {
    }

    public record Result(List<ResultUser> users, int count, int limit) {
    }

    public record ResultUser(String id, String username, String displayName, String email) {

        static ResultUser from(AppUser user) {
            return new ResultUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail());
        }
    }
}
