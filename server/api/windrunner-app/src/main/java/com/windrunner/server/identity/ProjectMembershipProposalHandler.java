package com.windrunner.server.identity;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.windrunner.server.identity.IdentityProposalSupport.*;

@Component
@RequiredArgsConstructor
final class ProjectMembershipProposalHandler implements ProposalHandler<IdentityProposalService.Draft> {
    private final ProjectAccessService access;
    private final ProjectMembershipService memberships;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final ProjectTeamRepository projectTeams;
    private final TeamService teams;
    private final AppUserRepository users;

    @Override
    public String entityType() {
        return IdentityProposalService.Kind.PROJECT_MEMBERSHIP.name();
    }

    @Override
    public void authorize(IdentityProposalService.Draft draft, AppUser actor) {
        access.requireProjectRole(required(draft.projectId(), "Project ID"), actor, ProjectRoles.OWNER);
    }

    @Override
    public ProposalPreparedChange prepare(IdentityProposalService.Draft draft, AppUser actor) {
        String projectId = required(draft.projectId(), "Project ID");
        var project = projects.findById(projectId).orElseThrow(() -> error(org.springframework.http.HttpStatus.NOT_FOUND, "Project not found"));
        String subjectType = required(draft.subjectType(), "Subject type");
        if (present(draft.userId()) == present(draft.teamId())) throw bad("Supply exactly one userId or teamId");

        String oldRole;
        Map<String, String> before = identity("projectId", projectId, "project", project.getName(), "subjectType", subjectType);
        if ("USER".equals(subjectType)) {
            String userId = required(draft.userId(), "User ID");
            before.put("userId", userId);
            before.put("user", display(memberUser(userId)));
            oldRole = projectMembers.findByProjectIdAndUserId(projectId, userId).map(member -> member.getRole()).orElse(null);
        } else if ("TEAM".equals(subjectType)) {
            String teamId = required(draft.teamId(), "Team ID");
            before.put("teamId", teamId);
            before.put("team", teams.getTeam(teamId).getName());
            oldRole = projectTeams.findByProjectIdAndTeamId(projectId, teamId).map(member -> member.getRole()).orElse(null);
        } else {
            throw bad("Subject type must be USER or TEAM");
        }
        membershipAction(draft.action(), oldRole);
        String newRole = "REMOVE".equals(draft.action()) ? null : projectRole(draft.role());
        access.requireAnotherOwnerBeforeRemovingOwner(projectId, ProjectRoles.OWNER.equals(oldRole) && !ProjectRoles.OWNER.equals(newRole));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", project.getUpdatedAt());
        if ("USER".equals(subjectType)) {
            projectMembers.findByProjectIdAndUserId(projectId, required(draft.userId(), "User ID"))
                    .ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        } else {
            projectTeams.findByProjectIdAndTeamId(projectId, required(draft.teamId(), "Team ID"))
                    .ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        }
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(IdentityProposalService.Draft draft, ProposalPreparedChange prepared, AppUser actor) {
        if ("USER".equals(draft.subjectType())) {
            memberships.applyUserOptimistic(draft.projectId(), draft.userId(), prepared.after().get("role"), draft.action(),
                    timestamp(prepared.before().get("updatedAt")), timestamp(prepared.before().get("membershipUpdatedAt")), actor);
        } else {
            memberships.applyTeamOptimistic(draft.projectId(), draft.teamId(), prepared.after().get("role"), draft.action(),
                    timestamp(prepared.before().get("updatedAt")), timestamp(prepared.before().get("membershipUpdatedAt")), actor);
        }
    }

    private AppUser memberUser(String id) {
        AppUser user = users.findById(id).orElseThrow(() -> error(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
        if (com.windrunner.server.auth.security.AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw bad("Super admin users cannot be members");
        }
        return user;
    }
}
