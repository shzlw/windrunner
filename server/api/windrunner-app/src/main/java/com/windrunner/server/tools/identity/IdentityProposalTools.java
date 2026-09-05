package com.windrunner.server.tools.identity;

import com.windrunner.server.identity.IdentityProposalService;
import com.windrunner.server.identity.IdentityProposalService.*;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.tools.ToolAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class IdentityProposalTools {
    private final IdentityProposalService proposals;
    private final ToolAuthorizationService authorization;
    private static final String COMMON = " Persist a pending proposal only; no underlying records are changed until the user accepts in chat. "
            + "Use focused read tools first to resolve exact IDs. Before ADD, search for an existing match; use UPDATE for a clear existing match, "
            + "ask about ambiguous matches, and only ADD after no clear match is found. For memberships, call fetch_membership first with the exact IDs. "
            + "Send 1-25 changes; only one change per target per call. Do not claim changes were applied. Authorization is enforced again on acceptance.";

    public List<LlmTool<?>> forMessage(ToolExecutionContext context, String messageId) {
        Objects.requireNonNull(context, "Tool context required");
        authorization.requireActor(context);
        return List.of(
            new LlmTool<>("propose_team_changes", "Propose ADD or UPDATE of a team. Fields: name, description. ADD requires name and ownerUserIds (1-25 exact user IDs); search fetch_teams by name first. UPDATE requires teamId; read fetch_team_details first. Omitted fields stay unchanged; empty description clears it. Admin only." + COMMON,
                TeamChanges.class, p -> proposals.create(context, messageId, Kind.TEAM, map(p == null ? null : p.changes(), d -> new Draft(d.action(), identifier(d.teamId()), null, null, null, null, d.ownerUserIds(), fields("name", d.name(), "description", d.description()))))),
            new LlmTool<>("propose_team_membership_changes", "Propose ADD, UPDATE or REMOVE of team membership using teamId and userId. ADD/UPDATE require role TEAM_OWNER or TEAM_MEMBER. REMOVE unlinks the user and may remove access inherited through the team. Admin only." + COMMON,
                TeamMembershipChanges.class, p -> proposals.create(context, messageId, Kind.TEAM_MEMBERSHIP, map(p == null ? null : p.changes(), d -> new Draft(d.action(), identifier(d.teamId()), identifier(d.userId()), null, null, d.role(), null, null)))),
            new LlmTool<>("propose_project_membership_changes", "Propose ADD, UPDATE or REMOVE of a project membership. Requires an active-context projectId, subjectType USER with userId only and an empty teamId, or subjectType TEAM with teamId only and an empty userId. ADD/UPDATE require OWNER, EDITOR or VIEWER role. Project owner required. REMOVE changes only this direct relationship; other access paths can remain. This grants project access, distinct from work-item assignment." + COMMON,
                ProjectMembershipChanges.class, p -> proposals.create(context, messageId, Kind.PROJECT_MEMBERSHIP, map(p == null ? null : p.changes(), d -> new Draft(d.action(), identifier(d.teamId()), identifier(d.userId()), identifier(d.projectId()), d.subjectType(), d.role(), null, null)))),
            new LlmTool<>("propose_user_profile_changes", "Propose UPDATE of user profile fields: username, email, displayName, title, bio, timezone. Resolve names with find_manageable_users, then read fetch_manageable_user by exact userId first. Omitted/null fields stay unchanged; use an empty string to explicitly clear email, displayName, title or bio. Admin only, respecting managed-user restrictions." + COMMON,
                UserProfileChanges.class, p -> proposals.create(context, messageId, Kind.USER_PROFILE, map(p == null ? null : p.changes(), d -> new Draft("UPDATE", null, identifier(d.userId()), null, null, null, null, fields("username", d.username(), "email", d.email(), "displayName", d.displayName(), "title", d.title(), "bio", d.bio(), "timezone", d.timezone()))))),
            new LlmTool<>("propose_user_access_changes", "Propose UPDATE of account status ACTIVE/INACTIVE or globalRole USER/ADMIN. Resolve names with find_manageable_users, then read fetch_manageable_user by exact userId first. Admin required for status, superadmin for globalRole. Superadmin accounts cannot be managed. Omitted fields stay unchanged." + COMMON,
                UserAccessChanges.class, p -> proposals.create(context, messageId, Kind.USER_ACCESS, map(p == null ? null : p.changes(), d -> new Draft("UPDATE", null, identifier(d.userId()), null, null, null, null, fields("status", d.status(), "globalRole", d.globalRole())))))
        );
    }

    private <T> List<Draft> map(List<T> changes, Function<T, Draft> convert) {
        if (changes == null) return null;
        return changes.stream().map(d -> d == null ? null : convert.apply(d)).toList();
    }

    private String identifier(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, String> fields(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) if (pairs[i + 1] != null) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
    public record TeamChanges(List<TeamChange> changes) { }
    public record TeamChange(String action, String teamId, String name, String description, List<String> ownerUserIds) { }
    public record TeamMembershipChanges(List<TeamMembershipChange> changes) { }
    public record TeamMembershipChange(String action, String teamId, String userId, String role) { }
    public record ProjectMembershipChanges(List<ProjectMembershipChange> changes) { }
    public record ProjectMembershipChange(String action, String projectId, String subjectType, String userId, String teamId, String role) { }
    public record UserProfileChanges(List<UserProfileChange> changes) { }
    public record UserProfileChange(String userId, String username, String email, String displayName, String title, String bio, String timezone) { }
    public record UserAccessChanges(List<UserAccessChange> changes) { }
    public record UserAccessChange(String userId, String status, String globalRole) { }
}
