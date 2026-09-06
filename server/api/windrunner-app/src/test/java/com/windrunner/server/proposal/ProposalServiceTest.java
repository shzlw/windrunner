package com.windrunner.server.proposal;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.api.UserResponse;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {
    @Mock
    ProposalRepository proposalRepository;
    @Mock
    AuthService authService;
    @Mock
    ChatSessionRepository chatSessionRepository;
    @Mock
    ChatMessageRepository chatMessageRepository;
    @Spy
    EntityIdGenerator entityIdGenerator = new EntityIdGenerator();
    @Mock
    TeamService teamService;
    @Mock
    UserAdminService userAdminService;
    @Mock
    ProjectMembershipService projectMembershipService;
    @Mock
    ProjectAccessService projectAccessService;
    @Mock
    TeamMemberRepository teamMemberRepository;
    @Mock
    ProjectMemberRepository projectMemberRepository;
    @Mock
    ProjectTeamRepository projectTeamRepository;
    @Mock
    ProjectRepository projectRepository;
    @Mock
    AppUserRepository appUserRepository;
    @Mock
    ToolAuthorizationService toolAuthorizationService;
    @InjectMocks
    ProposalService proposalService;
    AppUser actor;

    @BeforeEach
    void setup() {
        actor = new AppUser();
        actor.setId("admin");
        actor.setGlobalRole("ADMIN");
        actor.setStatus("ACTIVE");
    }

    void session() {
        when(authService.requireActiveActor(actor)).thenReturn(actor);
        when(chatSessionRepository.findByIdAndUserId("session", "admin")).thenReturn(Optional.of(new ChatSession()));
        lenient().when(toolAuthorizationService.requireActor(any())).thenReturn(actor);
        lenient().doAnswer(invocation -> {
            ToolExecutionContext context = invocation.getArgument(0);
            context.requireProjectId(invocation.getArgument(1));
            return actor;
        }).when(toolAuthorizationService).requireProjectOwner(any(), anyString());
    }

    void source() {
        session();
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        when(chatMessageRepository.findByIdAndSessionId("message", "session")).thenReturn(Optional.of(message));
    }

    ToolExecutionContext context(String... projects) {
        return new ToolExecutionContext(actor, "session", List.of(projects));
    }

    ProposalDraft teamMembership(String action, String role) {
        return new ProposalDraft(action, "team", "user", null, null, role, null, null);
    }

    void memberTarget() {
        Team team = new Team();
        team.setId("team");
        team.setName("Engineering");
        when(teamService.getTeam("team")).thenReturn(team);
        AppUser user = new AppUser();
        user.setId("user");
        user.setUsername("alice");
        user.setGlobalRole("USER");
        when(appUserRepository.findById("user")).thenReturn(Optional.of(user));
    }

    TeamMember member(String role) {
        TeamMember m = new TeamMember();
        m.setRole(role);
        return m;
    }

    Proposal stored(ProposalDraft draft, ProposalKind kind, Map<String, String> before, Map<String, String> after) {
        Proposal p = new Proposal();
        p.setId("proposal");
        p.setKind(kind.name());
        p.setStatus("PENDING");
        p.setDraftJson(JsonUtils.toJson(draft));
        p.setBeforeJson(JsonUtils.toJson(before));
        p.setAfterJson(JsonUtils.toJson(after));
        when(proposalRepository.findForDecision("IDENTITY", "proposal", "session", "admin")).thenReturn(Optional.of(p));
        lenient().when(proposalRepository.claimForDecision("proposal", "session", "admin")).thenReturn(1);
        return p;
    }

    void fails(HttpStatus status, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
        assertThatThrownBy(work).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void ordinaryUserCannotProposeAdminWrites() {
        source();
        actor.setGlobalRole("USER");
        doCallRealMethod().when(teamService).requireAdmin(actor);
        fails(HttpStatus.FORBIDDEN, () -> proposalService.create(context(), "message", ProposalKind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_MEMBER"))));
        verifyNoInteractions(appUserRepository, teamMemberRepository, proposalRepository);
    }

    @Test
    void sessionOwnedBySomeoneElseIsRejectedBeforeReadingSource() {
        when(toolAuthorizationService.requireActor(any())).thenReturn(actor);
        when(authService.requireActiveActor(actor)).thenReturn(actor);
        fails(HttpStatus.NOT_FOUND, () -> proposalService.create(context(), "message", ProposalKind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_MEMBER"))));
        verifyNoInteractions(chatMessageRepository, proposalRepository, teamService);
    }

    @Test
    void projectOutsideContextIsRejectedEvenForAdmin() {
        source();
        ProposalDraft draft = new ProposalDraft("ADD", null, "user", "other", "USER", "VIEWER", null, null);
        fails(HttpStatus.FORBIDDEN, () -> proposalService.create(context("selected"), "message", ProposalKind.PROJECT_MEMBERSHIP, List.of(draft)));
        verifyNoInteractions(projectAccessService, proposalRepository, projectMemberRepository);
    }

    @Test
    void projectOwnerCheckCannotBeBypassedByToolArguments() {
        source();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(projectAccessService).requireProjectRole("project", actor, "OWNER");
        ProposalDraft draft = new ProposalDraft("ADD", null, "user", "project", "USER", "VIEWER", null, null);
        fails(HttpStatus.FORBIDDEN, () -> proposalService.create(context("project"), "message", ProposalKind.PROJECT_MEMBERSHIP, List.of(draft)));
        verifyNoInteractions(proposalRepository, projectMemberRepository);
    }

    @Test
    void existingMembershipCannotBeAddedAgain() {
        source();
        memberTarget();
        when(teamMemberRepository.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(member("TEAM_MEMBER")));
        fails(HttpStatus.CONFLICT, () -> proposalService.create(context(), "message", ProposalKind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_OWNER"))));
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void lastTeamOwnerCannotBeDemoted() {
        source();
        memberTarget();
        when(teamMemberRepository.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(member("TEAM_OWNER")));
        when(teamMemberRepository.countOwners("team")).thenReturn(1L);
        fails(HttpStatus.BAD_REQUEST, () -> proposalService.create(context(), "message", ProposalKind.TEAM_MEMBERSHIP, List.of(teamMembership("UPDATE", "TEAM_MEMBER"))));
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void creationPersistsOnlyPendingProposalAndReturnsCompactResult() {
        source();
        memberTarget();
        var result = proposalService.create(context(), "message", ProposalKind.TEAM_MEMBERSHIP, List.of(teamMembership("ADD", "TEAM_MEMBER")));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo("PENDING");
        assertThat(result.getFirst().changedFields()).containsExactly("role");
        verify(proposalRepository).insert(anyString(), eq("IDENTITY"), eq("session"), eq("message"), eq("admin"), eq("TEAM_MEMBERSHIP"), anyString(), anyString(), anyString());
        verify(teamService, never()).addMember(any(), any(), any());
        verifyNoInteractions(projectMembershipService);
    }

    @Test
    void revokedPermissionBlocksAcceptance() {
        session();
        actor.setGlobalRole("USER");
        stored(teamMembership("ADD", "TEAM_MEMBER"), ProposalKind.TEAM_MEMBERSHIP, Map.of(), Map.of());
        doCallRealMethod().when(teamService).requireAdmin(actor);
        fails(HttpStatus.FORBIDDEN, () -> proposalService.decide("session", "proposal", "ACCEPT", actor));
        verify(proposalRepository, never()).decide(anyString(), anyString(), anyString(), anyString());
        verify(teamService, never()).addMember(any(), any(), any());
    }

    @Test
    void disabledActorCannotAccept() {
        when(authService.requireActiveActor(actor)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        fails(HttpStatus.UNAUTHORIZED, () -> proposalService.decide("session", "proposal", "ACCEPT", actor));
        verifyNoInteractions(proposalRepository, teamService);
    }

    @Test
    void staleMembershipIsNotApplied() {
        session();
        memberTarget();
        when(teamMemberRepository.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(member("TEAM_MEMBER")));
        stored(teamMembership("REMOVE", null), ProposalKind.TEAM_MEMBERSHIP, Map.of("role", "TEAM_OWNER"), Map.of());
        fails(HttpStatus.CONFLICT, () -> proposalService.decide("session", "proposal", "ACCEPT", actor));
        verify(teamService, never()).removeMember(any(), any(), any());
        verify(proposalRepository, never()).decide(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void acceptedChangeUsesExistingServiceAndCannotBeReplayed() {
        session();
        memberTarget();
        Map<String, String> before = new LinkedHashMap<>(Map.of("teamId", "team", "team", "Engineering", "userId", "user", "user", "alice"));
        before.put("role", null);
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", "TEAM_MEMBER");
        Proposal p = stored(teamMembership("ADD", "TEAM_MEMBER"), ProposalKind.TEAM_MEMBERSHIP, before, after);
        when(proposalRepository.decide("proposal", "session", "admin", "APPLIED")).thenReturn(1);
        assertThat(proposalService.decide("session", "proposal", "ACCEPT", actor).status()).isEqualTo("APPLIED");
        verify(teamService).applyMembershipOptimistic(eq("team"), eq("user"), eq("TEAM_MEMBER"), eq("ADD"), isNull(), isNull(), same(actor));
        assertThat(p.getStatus()).isEqualTo("APPLIED");
        fails(HttpStatus.CONFLICT, () -> proposalService.decide("session", "proposal", "ACCEPT", actor));
        verify(proposalRepository).decide("proposal", "session", "admin", "APPLIED");
    }

    @Test
    void rejectDoesNotWriteUnderlyingRecord() {
        session();
        stored(teamMembership("ADD", "TEAM_MEMBER"), ProposalKind.TEAM_MEMBERSHIP, Map.of(), Map.of());
        when(proposalRepository.decide("proposal", "session", "admin", "REJECTED")).thenReturn(1);
        assertThat(proposalService.decide("session", "proposal", "REJECT", actor).status()).isEqualTo("REJECTED");
        verify(teamService, never()).addMember(any(), any(), any());
        verifyNoInteractions(teamMemberRepository, projectMembershipService, appUserRepository);
    }

    @Test
    void profileToolCannotSmuggleGlobalRole() {
        source();
        ProposalDraft d = new ProposalDraft("UPDATE", null, "user", null, null, null, null, Map.of("globalRole", "ADMIN"));
        fails(HttpStatus.BAD_REQUEST, () -> proposalService.create(context(), "message", ProposalKind.USER_PROFILE, List.of(d)));
        verify(userAdminService, never()).validateUpdate(any(), any(), any());
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void adminCannotProposeGlobalRoleChanges() {
        source();
        ProposalDraft d = new ProposalDraft("UPDATE", null, "user", null, null, null, null, Map.of("globalRole", "ADMIN"));
        fails(HttpStatus.FORBIDDEN, () -> proposalService.create(context(), "message", ProposalKind.USER_ACCESS, List.of(d)));
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void partialProfileUpdatesPreserveOtherFieldsAndExplicitlyClearTitle() {
        source();
        when(userAdminService.getUser("user", actor)).thenReturn(UserResponse.builder().id("user").username("alice")
                .email("alice@example.com").displayName("Alice").title("Engineer").bio("Biography")
                .timezone("UTC").status("ACTIVE").globalRole("USER").build());
        ProposalDraft d = new ProposalDraft("UPDATE", null, "user", null, null, null, null, Map.of("title", ""));
        proposalService.create(context(), "message", ProposalKind.USER_PROFILE, List.of(d));
        ArgumentCaptor<UpdateUserRequest> request = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(userAdminService).validateUpdate(eq("user"), request.capture(), same(actor));
        assertThat(request.getValue().getTitle()).isNull();
        assertThat(request.getValue().getBio()).isEqualTo("Biography");
        assertThat(request.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(request.getValue().getGlobalRole()).isNull();
        verify(userAdminService, never()).updateUser(any(), any(), any());
    }
}
