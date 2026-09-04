package com.windrunner.server.tools.identity;

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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class FetchUserDetailsTool implements Tool<FetchUserDetailsTool.Parameters> {

    private static final int MAX_USER_IDS = 100;
    private static final String PROMPT_NAME = "fetch-user-details-tool.md";

    private final AppUserRepository appUserRepository;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_user_details";
    }

    @Override
    public boolean parallelSafe() {
        return true;
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
        List<String> userIds = parameters == null || parameters.userIds() == null
                ? List.of()
                : new LinkedHashSet<>(parameters.userIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList()).stream().toList();
        if (userIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one user id is required");
        }
        if (userIds.size() > MAX_USER_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 user ids can be requested");
        }

        authorization.requireContext(context);
        Map<String, AppUser> usersById = appUserRepository.findActiveUsersByIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<ResultUser> users = userIds.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(ResultUser::from)
                .toList();
        return new Result(users, users.size(), userIds.size());
    }

    public record Parameters(List<String> userIds) {
    }

    public record Result(List<ResultUser> users, int count, int requestedCount) {
    }

    public record ResultUser(String id, String username, String displayName, String email, String title, String bio) {

        static ResultUser from(AppUser user) {
            return new ResultUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getTitle(), user.getBio());
        }
    }
}
