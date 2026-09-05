package com.windrunner.server.tools.identity;

import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.tools.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class FetchManageableUserTool implements Tool<FetchManageableUserTool.Parameters> {
    private final ToolAuthorizationService authorization;
    private final UserAdminService users;
    public String name() { return "fetch_manageable_user"; }
    public String description() { return "Read one exact userId for an admin profile/access proposal, including inactive users. Returns username, email, displayName, title, bio (up to 4000 characters), timezone, status, globalRole. Admin only, respecting managed-user restrictions. No credentials are returned."; }
    public Class<Parameters> parametersType() { return Parameters.class; }
    public boolean parallelSafe() { return true; }
    public Object execute(Parameters p, ToolExecutionContext context) {
        var actor = authorization.requireAdmin(context);
        if (p == null || p.userId() == null || p.userId().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        var user = users.getUser(p.userId(), actor);
        boolean truncated = user.bio() != null && user.bio().length() > 4000;
        return new Result(user.id(), user.username(), user.email(), user.displayName(), user.title(),
                truncated ? user.bio().substring(0, 4000) : user.bio(), truncated, user.timezone(), user.status(), user.globalRole());
    }
    public record Parameters(String userId) { }
    public record Result(String id, String username, String email, String displayName, String title,
                         String bio, boolean bioTruncated, String timezone, String status, String globalRole) { }
}
