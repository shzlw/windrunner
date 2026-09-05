package com.windrunner.server.tools.identity;

import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.tools.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FindManageableUsersTool implements Tool<FindManageableUsersTool.Parameters> {
    private final ToolAuthorizationService authorization;
    private final UserAdminService users;
    public String name() { return "find_manageable_users"; }
    public String description() { return "Admin-only focused username, display name or email search for user profile/access proposals, including INACTIVE accounts. Requires a nonblank query. Returns up to 20 manageable candidates (id, username, displayName, email, status, globalRole) and hasMore. Narrow the query if truncated or ambiguous; never guess. Read fetch_manageable_user for the chosen exact ID before proposing changes."; }
    public Class<Parameters> parametersType() { return Parameters.class; }
    public boolean parallelSafe() { return true; }
    public Object execute(Parameters p, ToolExecutionContext context) {
        var actor = authorization.requireAdmin(context);
        var candidates = users.findManageableUsers(p == null ? null : p.query(), 21, actor);
        return new Result(candidates.stream().limit(20).map(user -> new Candidate(user.id(), user.username(), user.displayName(), user.email(), user.status(), user.globalRole())).toList(), candidates.size() > 20, 20);
    }
    public record Parameters(String query) { }
    public record Candidate(String id, String username, String displayName, String email, String status, String globalRole) { }
    public record Result(List<Candidate> users, boolean hasMore, int limit) { }
}
