package com.windrunner.server.proposal.identity;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.proposal.ProposalKind;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.windrunner.server.proposal.identity.IdentityProposalUtils.*;

@Component
@RequiredArgsConstructor
final class ProjectMembershipProposalHandler implements ProposalHandler<ProposalDraft> {
    private final ProjectAccessService projectAccessService;
    private final ProjectMembershipService projectMembershipService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamService teamService;
    private final AppUserRepository appUserRepository;

    @Override
    public String getEntityType() {
        return ProposalKind.PROJECT_MEMBERSHIP.name();
    }

    @Override
    public void authorize(ProposalDraft draft, AppUser actor) {
        projectAccessService.requireProjectRole(requireValue(draft.projectId(), "Project ID"), actor, ProjectRoles.OWNER);
    }

    @Override
    public ProposalPreparedChange prepare(ProposalDraft draft, AppUser actor) {
        String projectId = requireValue(draft.projectId(), "Project ID");
        var project = projectRepository.findById(projectId).orElseThrow(() -> createResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Project not found"));
        String subjectType = requireValue(draft.subjectType(), "Subject type");
        if (hasText(draft.userId()) == hasText(draft.teamId())) throw createBadRequestException("Supply exactly one userId or teamId");

        String oldRole;
        Map<String, String> before = createIdentityMap("projectId", projectId, "project", project.getName(), "subjectType", subjectType);
        if ("USER".equals(subjectType)) {
            String userId = requireValue(draft.userId(), "User ID");
            before.put("userId", userId);
            before.put("user", getDisplayName(requireMemberUser(userId)));
            oldRole = projectMemberRepository.findByProjectIdAndUserId(projectId, userId).map(member -> member.getRole()).orElse(null);
        } else if ("TEAM".equals(subjectType)) {
            String teamId = requireValue(draft.teamId(), "Team ID");
            before.put("teamId", teamId);
            before.put("team", teamService.getTeam(teamId).getName());
            oldRole = projectTeamRepository.findByProjectIdAndTeamId(projectId, teamId).map(member -> member.getRole()).orElse(null);
        } else {
            throw createBadRequestException("Subject type must be USER or TEAM");
        }
        validateMembershipAction(draft.action(), oldRole);
        String newRole = "REMOVE".equals(draft.action()) ? null : normalizeProjectRole(draft.role());
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(projectId, ProjectRoles.OWNER.equals(oldRole) && !ProjectRoles.OWNER.equals(newRole));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", project.getUpdatedAt());
        if ("USER".equals(subjectType)) {
            projectMemberRepository.findByProjectIdAndUserId(projectId, requireValue(draft.userId(), "User ID"))
                    .ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        } else {
            projectTeamRepository.findByProjectIdAndTeamId(projectId, requireValue(draft.teamId(), "Team ID"))
                    .ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        }
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(ProposalDraft draft, ProposalPreparedChange prepared, AppUser actor) {
        if ("USER".equals(draft.subjectType())) {
            projectMembershipService.applyUserOptimistic(draft.projectId(), draft.userId(), prepared.after().get("role"), draft.action(),
                    parseTimestamp(prepared.before().get("updatedAt")), parseTimestamp(prepared.before().get("membershipUpdatedAt")), actor);
        } else {
            projectMembershipService.applyTeamOptimistic(draft.projectId(), draft.teamId(), prepared.after().get("role"), draft.action(),
                    parseTimestamp(prepared.before().get("updatedAt")), parseTimestamp(prepared.before().get("membershipUpdatedAt")), actor);
        }
    }

    private AppUser requireMemberUser(String id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> createResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw createBadRequestException("Super admin users cannot be members");
        }
        return user;
    }
}
