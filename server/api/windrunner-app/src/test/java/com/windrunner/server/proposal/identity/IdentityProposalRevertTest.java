package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityProposalRevertTest {

    @Test
    void reversesAnAppliedTeamMembershipAddition() {
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TeamMember current = new TeamMember();
        current.setRole("TEAM_MEMBER");
        when(members.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.of(current));
        TeamMembershipProposalHandler handler = new TeamMembershipProposalHandler(
                mock(TeamService.class), members, mock(AppUserRepository.class));

        ProposalDraft revert = handler.buildRevert(membershipChange("ADD", null, "TEAM_MEMBER"), new AppUser());

        assertThat(revert.action()).isEqualTo("REMOVE");
        assertThat(revert.teamId()).isEqualTo("team-1");
        assertThat(revert.userId()).isEqualTo("user-1");
    }

    @Test
    void rejectsMembershipRevertAfterItsRoleChangedAgain() {
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TeamMember current = new TeamMember();
        current.setRole("TEAM_OWNER");
        when(members.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.of(current));
        TeamMembershipProposalHandler handler = new TeamMembershipProposalHandler(
                mock(TeamService.class), members, mock(AppUserRepository.class));

        assertThatThrownBy(() -> handler.buildRevert(
                membershipChange("ADD", null, "TEAM_MEMBER"), new AppUser()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private ProposalChange membershipChange(String action, String beforeRole, String afterRole) {
        ProposalDraft draft = new ProposalDraft(action, "team-1", "user-1", null, null,
                afterRole, null, null);
        Map<String, String> before = new LinkedHashMap<>();
        before.put("role", beforeRole);
        Map<String, String> after = new LinkedHashMap<>();
        after.put("role", afterRole);
        ProposalChange change = new ProposalChange();
        change.setPayload(JsonUtils.toJson(draft));
        change.setBeforeSnapshot(JsonUtils.toJson(before));
        change.setAfterSnapshot(JsonUtils.toJson(after));
        return change;
    }
}
