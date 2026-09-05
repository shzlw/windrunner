package com.windrunner.server.proposal.identity;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.proposal.ProposalService;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.windrunner.server.proposal.identity.IdentityProposalSupport.*;

@Component
@RequiredArgsConstructor
final class ProjectMembershipProposalHandler implements ProposalHandler<ProposalService.Draft> {
    private final ProjectAccessService projectAccessService;
    private final ProjectMembershipService projectMembershipService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamService teamService;
    private final AppUserRepository appUserRepository;

    @Override
    public String entityType() {
        return ProposalService.Kind.PROJECT_MEMBERSHIP.name();
    }

    @Override
    public void authorize(ProposalService.Draft draft, AppUser actor) {
        projectAccessService.requireProjectRole(required(draft.projectId(), "Project ID"), actor, ProjectRoles.OWNER);
    }

    @Override
    public ProposalPreparedChange prepare(ProposalService.Draft draft, AppUser actor) {
        String projectId = required(draft.projectId(), "Project ID");
        var project = projectRepository.findById(projectId).orElseThrow(() -> error(org.springframework.http.HttpStatus.NOT_FOUND, "Project not found"));
        String subjectType = required(draft.subjectType(), "Subject type");
        if (present(draft.userId()) == present(draft.teamId())) throw bad("Supply exactly one userId or teamId");

        String oldRole;
        Map<String, String> before = identity("projectId", projectId, "project", project.getName(), "subjectType", subjectType);
        if ("USER".equals(subjectType)) {
            String userId = required(draft.userId(), "User ID");
            before.put("userId", userId);
            before.put("user", display(memberUser(userId)));
            oldRole = projectMemberRepository.findByProjectIdAndUserId(projectId, userId).map(member -> member.getRole()).orElse(null);
        } else if ("TEAM".equals(subjectType)) {
            String teamId = required(draft.teamId(), "Team ID");
            before.put("teamId", teamId);
            before.put("team", teamService.getTeam(teamId).getName());
            oldRole = projectTeamRepository.findByProjectIdAndTeamId(projectId, teamId).map(member -> member.getRole()).orElse(null);
        } else {
            throw bad("Subject type must be USER or TEAM");
        }
        membershipAction(draft.action(), oldRole);
        String newRole = "REMOVE".equals(draft.action()) ? null : projectRole(draft.role());
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(projectId, ProjectRoles.OWNER.equals(oldRole) && !ProjectRoles.OWNER.equals(newRole));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", project.getUpdatedAt());
        if ("USER".equals(subjectType)) {
            projectMemberRepository.findByProjectIdAndUserId(projectId, required(draft.userId(), "User ID"))
                    .ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        } else {
            projectTeamRepository.findByProjectIdAndTeamId(projectId, required(draft.teamId(), "Team ID"))
                    .ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        }
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(ProposalService.Draft draft, ProposalPreparedChange prepared, AppUser actor) {
        if ("USER".equals(draft.subjectType())) {
            projectMembershipService.applyUserOptimistic(draft.projectId(), draft.userId(), prepared.after().get("role"), draft.action(),
                    timestamp(prepared.before().get("updatedAt")), timestamp(prepared.before().get("membershipUpdatedAt")), actor);
        } else {
            projectMembershipService.applyTeamOptimistic(draft.projectId(), draft.teamId(), prepared.after().get("role"), draft.action(),
                    timestamp(prepared.before().get("updatedAt")), timestamp(prepared.before().get("membershipUpdatedAt")), actor);
        }
    }

    private AppUser memberUser(String id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> error(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw bad("Super admin users cannot be members");
        }
        return user;
    }
}
