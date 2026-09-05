package com.windrunner.server.identity;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.persistence.*;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.*;
import com.windrunner.server.project.persistence.*;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.*;
import com.windrunner.server.team.persistence.*;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.*;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.windrunner.server.identity.IdentityProposalService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityProposalServiceTest {
    @Mock IdentityProposalRepository proposals;
    @Mock AuthService auth;
    @Mock ChatSessionRepository sessions;
    @Mock ChatMessageRepository messages;
    @Spy EntityIdGenerator ids = new EntityIdGenerator();
    @Mock TeamService teams;
    @Mock UserAdminService users;
    @Mock ProjectMembershipService memberships;
    @Mock ProjectAccessService access;
    @Mock TeamMemberRepository teamMembers;
    @Mock ProjectMemberRepository projectMembers;
    @Mock ProjectTeamRepository projectTeams;
    @Mock ProjectRepository projects;
    @Mock AppUserRepository userRepository;
    @Mock ToolAuthorizationService authorization;
    @InjectMocks IdentityProposalService service;
    AppUser actor;

    @BeforeEach void setup() {
        actor = new AppUser(); actor.setId("admin"); actor.setGlobalRole("ADMIN"); actor.setStatus("ACTIVE");
    }
    void session() {
        when(auth.requireActiveActor(actor)).thenReturn(actor);
        when(sessions.findByIdAndUserId("session", "admin")).thenReturn(Optional.of(new ChatSession()));
        lenient().when(authorization.requireActor(any())).thenReturn(actor);
        lenient().doAnswer(invocation -> {
            ToolExecutionContext context = invocation.getArgument(0);
            context.requireProjectId(invocation.getArgument(1));
            return actor;
        }).when(authorization).requireProjectOwner(any(), anyString());
    }
    void source() {
        session();
        ChatMessage message = new ChatMessage(); message.setRole("user");
        when(messages.findByIdAndSessionId("message", "session")).thenReturn(Optional.of(message));
    }
    ToolExecutionContext context(String... projects) { return new ToolExecutionContext(actor, "session", List.of(projects)); }
    Draft teamMembership(String action, String role) { return new Draft(action, "team", "user", null, null, role, null, null); }
    void memberTarget() {
        Team team = new Team(); team.setId("team"); team.setName("Engineering");
        when(teams.getTeam("team")).thenReturn(team);
        AppUser user = new AppUser(); user.setId("user"); user.setUsername("alice"); user.setGlobalRole("USER");
        when(userRepository.findById("user")).thenReturn(Optional.of(user));
    }
    TeamMember member(String role) { TeamMember m = new TeamMember(); m.setRole(role); return m; }
    IdentityProposal stored(Draft draft, Kind kind, Map<String, String> before, Map<String, String> after) {
        IdentityProposal p = new IdentityProposal(); p.setId("proposal"); p.setKind(kind.name()); p.setStatus("PENDING");
        p.setDraftJson(JsonUtils.toJson(draft)); p.setBeforeJson(JsonUtils.toJson(before)); p.setAfterJson(JsonUtils.toJson(after));
        when(proposals.findForDecision("proposal", "session", "admin")).thenReturn(Optional.of(p));
        lenient().when(proposals.claimForDecision("proposal", "session", "admin")).thenReturn(1);
        return p;
    }
    void fails(HttpStatus status, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
        assertThatThrownBy(work).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test void ordinaryUserCannotProposeAdminWrites() {
        source(); actor.setGlobalRole("USER");
        doCallRealMethod().when(teams).requireAdmin(actor);
        fails(HttpStatus.FORBIDDEN, () -> service.create(context(), "message", Kind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_MEMBER"))));
        verifyNoInteractions(userRepository, teamMembers, proposals);
    }
    @Test void sessionOwnedBySomeoneElseIsRejectedBeforeReadingSource() {
        when(authorization.requireActor(any())).thenReturn(actor);
        when(auth.requireActiveActor(actor)).thenReturn(actor);
        fails(HttpStatus.NOT_FOUND, () -> service.create(context(), "message", Kind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_MEMBER"))));
        verifyNoInteractions(messages, proposals, teams);
    }
    @Test void projectOutsideContextIsRejectedEvenForAdmin() {
        source();
        Draft draft = new Draft("ADD", null, "user", "other", "USER", "VIEWER", null, null);
        fails(HttpStatus.FORBIDDEN, () -> service.create(context("selected"), "message", Kind.PROJECT_MEMBERSHIP, List.of(draft)));
        verifyNoInteractions(access, proposals, projectMembers);
    }
    @Test void projectOwnerCheckCannotBeBypassedByToolArguments() {
        source();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireProjectRole("project", actor, "OWNER");
        Draft draft = new Draft("ADD", null, "user", "project", "USER", "VIEWER", null, null);
        fails(HttpStatus.FORBIDDEN, () -> service.create(context("project"), "message", Kind.PROJECT_MEMBERSHIP, List.of(draft)));
        verifyNoInteractions(proposals, projectMembers);
    }
    @Test void existingMembershipCannotBeAddedAgain() {
        source(); memberTarget();
        when(teamMembers.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(member("TEAM_MEMBER")));
        fails(HttpStatus.CONFLICT, () -> service.create(context(), "message", Kind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_OWNER"))));
        verifyNoInteractions(proposals);
    }
    @Test void lastTeamOwnerCannotBeDemoted() {
        source(); memberTarget();
        when(teamMembers.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(member("TEAM_OWNER")));
        when(teamMembers.countOwners("team")).thenReturn(1L);
        fails(HttpStatus.BAD_REQUEST, () -> service.create(context(), "message", Kind.TEAM_MEMBERSHIP, List.of(teamMembership("UPDATE", "TEAM_MEMBER"))));
        verifyNoInteractions(proposals);
    }
    @Test void creationPersistsOnlyPendingProposalAndReturnsCompactResult() {
        source(); memberTarget();
        var result = service.create(context(), "message", Kind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_MEMBER")));
        assertThat(result).hasSize(1); assertThat(result.getFirst().status()).isEqualTo("PENDING");
        assertThat(result.getFirst().changedFields()).containsExactly("role");
        verify(proposals).insert(anyString(), eq("session"), eq("message"), eq("admin"), eq("TEAM_MEMBERSHIP"), anyString(), anyString(), anyString());
        verify(teams, never()).addMember(any(), any(), any());
        verifyNoInteractions(memberships);
    }
    @Test void revokedPermissionBlocksAcceptance() {
        session(); actor.setGlobalRole("USER");
        stored(teamMembership("ADD", "TEAM_MEMBER"), Kind.TEAM_MEMBERSHIP, Map.of(), Map.of());
        doCallRealMethod().when(teams).requireAdmin(actor);
        fails(HttpStatus.FORBIDDEN, () -> service.decide("session", "proposal", "ACCEPT", actor));
        verify(proposals, never()).decide(anyString(), anyString(), anyString(), anyString());
        verify(teams, never()).addMember(any(), any(), any());
    }
    @Test void disabledActorCannotAccept() {
        when(auth.requireActiveActor(actor)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        fails(HttpStatus.UNAUTHORIZED, () -> service.decide("session", "proposal", "ACCEPT", actor));
        verifyNoInteractions(proposals, teams);
    }
    @Test void staleMembershipIsNotApplied() {
        session(); memberTarget();
        when(teamMembers.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(member("TEAM_MEMBER")));
        stored(teamMembership("REMOVE", null), Kind.TEAM_MEMBERSHIP, Map.of("role", "TEAM_OWNER"), Map.of());
        fails(HttpStatus.CONFLICT, () -> service.decide("session", "proposal", "ACCEPT", actor));
        verify(teams, never()).removeMember(any(), any(), any());
        verify(proposals, never()).decide(anyString(), anyString(), anyString(), anyString());
    }
    @Test void acceptedChangeUsesExistingServiceAndCannotBeReplayed() {
        session(); memberTarget();
        Map<String,String> before = new LinkedHashMap<>(Map.of("teamId", "team", "team", "Engineering", "userId", "user", "user", "alice"));
        before.put("role", null);
        Map<String,String> after = new LinkedHashMap<>(before); after.put("role", "TEAM_MEMBER");
        IdentityProposal p = stored(teamMembership("ADD", "TEAM_MEMBER"), Kind.TEAM_MEMBERSHIP, before, after);
        when(proposals.decide("proposal", "session", "admin", "APPLIED")).thenReturn(1);
        assertThat(service.decide("session", "proposal", "ACCEPT", actor).status()).isEqualTo("APPLIED");
        verify(teams).applyMembershipOptimistic(eq("team"), eq("user"), eq("TEAM_MEMBER"), eq("ADD"), isNull(), isNull(), same(actor));
        assertThat(p.getStatus()).isEqualTo("APPLIED");
        fails(HttpStatus.CONFLICT, () -> service.decide("session", "proposal", "ACCEPT", actor));
        verify(proposals).decide("proposal", "session", "admin", "APPLIED");
    }
    @Test void rejectDoesNotWriteUnderlyingRecord() {
        session(); stored(teamMembership("ADD", "TEAM_MEMBER"), Kind.TEAM_MEMBERSHIP, Map.of(), Map.of());
        when(proposals.decide("proposal", "session", "admin", "REJECTED")).thenReturn(1);
        assertThat(service.decide("session", "proposal", "REJECT", actor).status()).isEqualTo("REJECTED");
        verify(teams, never()).addMember(any(), any(), any());
        verifyNoInteractions(teamMembers, memberships, userRepository);
    }
    @Test void profileToolCannotSmuggleGlobalRole() {
        source();
        Draft d = new Draft("UPDATE", null, "user", null, null, null, null, Map.of("globalRole", "ADMIN"));
        fails(HttpStatus.BAD_REQUEST, () -> service.create(context(), "message", Kind.USER_PROFILE, List.of(d)));
        verify(users, never()).validateUpdate(any(), any(), any());
        verifyNoInteractions(proposals);
    }
    @Test void adminCannotProposeGlobalRoleChanges() {
        source();
        Draft d = new Draft("UPDATE", null, "user", null, null, null, null, Map.of("globalRole", "ADMIN"));
        fails(HttpStatus.FORBIDDEN, () -> service.create(context(), "message", Kind.USER_ACCESS, List.of(d)));
        verifyNoInteractions(proposals);
    }
    @Test void partialProfileUpdatesPreserveOtherFieldsAndExplicitlyClearTitle() {
        source();
        when(users.getUser("user", actor)).thenReturn(UserResponse.builder().id("user").username("alice")
                .email("alice@example.com").displayName("Alice").title("Engineer").bio("Biography")
                .timezone("UTC").status("ACTIVE").globalRole("USER").build());
        Draft d = new Draft("UPDATE", null, "user", null, null, null, null, Map.of("title", ""));
        service.create(context(), "message", Kind.USER_PROFILE, List.of(d));
        ArgumentCaptor<UpdateUserRequest> request = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(users).validateUpdate(eq("user"), request.capture(), same(actor));
        assertThat(request.getValue().getTitle()).isNull();
        assertThat(request.getValue().getBio()).isEqualTo("Biography");
        assertThat(request.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(request.getValue().getGlobalRole()).isNull();
        verify(users, never()).updateUser(any(), any(), any());
    }
}
