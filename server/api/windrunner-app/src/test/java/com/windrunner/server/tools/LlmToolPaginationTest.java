package com.windrunner.server.tools;

import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.tools.identity.FetchTeamMembersTool;
import com.windrunner.server.tools.identity.FetchTeamProjectsTool;
import com.windrunner.server.tools.identity.FetchProjectAssigneesTool;
import com.windrunner.server.tools.work.FetchEntriesTool;
import com.windrunner.server.tools.work.FetchProjectBlockersTool;
import com.windrunner.server.tools.work.FetchProjectSummaryTool;
import com.windrunner.server.tools.work.FetchRelationshipsTool;
import com.windrunner.server.tools.work.FetchWorkItemsTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import com.windrunner.server.search.SearchNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class LlmToolPaginationTest {

    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private WorkItemAssigneeRepository workItemAssigneeRepository;
    @Mock
    private SearchNormalizer searchNormalizer;
    @Mock
    private EntryService entryService;
    @Mock
    private RelationshipService relationshipService;
    @Mock
    private RelationshipRepository relationshipRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ProjectTeamRepository projectTeamRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private ToolAuthorizationService authorization;

    private final ToolExecutionContext context = context();

    @BeforeEach
    void authorizeToolReads() {
        when(authorization.requireProject(any(), any()))
                .thenAnswer(invocation -> ((ToolExecutionContext) invocation.getArgument(0))
                        .requireProjectId((String) invocation.getArgument(1)));
    }

    @Test
    void fetchWorkItemsUsesDatabasePageAndBatchesAssignees() {
        WorkItem item = workItem("item-1");
        WorkItemAssignee assignee = new WorkItemAssignee();
        assignee.setWorkItemId("item-1");
        assignee.setAssigneeType("USER");
        assignee.setAssigneeId("user-1");
        when(workItemRepository.findPageForProject("project-1", null, null, null, null, 50, 100L))
                .thenReturn(List.of(item));
        when(workItemRepository.countForProject("project-1", null, null, null, null)).thenReturn(101L);
        when(workItemAssigneeRepository.findByWorkItemIds(List.of("item-1"))).thenReturn(List.of(assignee));

        FetchWorkItemsTool.Response response = (FetchWorkItemsTool.Response) new FetchWorkItemsTool(
                workItemRepository, workItemAssigneeRepository, searchNormalizer, authorization)
                .execute(new FetchWorkItemsTool.Parameters("project-1", null, 50, 100), context);

        assertThat(response.count()).isOne();
        assertThat(response.total()).isEqualTo(101);
        assertThat(response.offset()).isEqualTo(100);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.workItems()).singleElement().satisfies(result ->
                assertThat(result.assignees()).containsExactly(assignee));
        verify(workItemRepository, never()).findByProjectId("project-1");
    }

    @Test
    void fetchWorkItemsUsesBoundedSearchQueryAndReportsMorePages() {
        WorkItem item = workItem("item-1");
        when(searchNormalizer.normalize("deployment")).thenReturn("deploy");
        when(workItemRepository.searchInProjectPage("project-1", "deploy", "deployment", 20, 0L))
                .thenReturn(List.of(item));
        when(workItemRepository.countSearchInProject("project-1", "deploy", "deployment")).thenReturn(21L);
        when(workItemAssigneeRepository.findByWorkItemIds(List.of("item-1"))).thenReturn(List.of());

        FetchWorkItemsTool.Response response = (FetchWorkItemsTool.Response) new FetchWorkItemsTool(
                workItemRepository, workItemAssigneeRepository, searchNormalizer, authorization)
                .execute(new FetchWorkItemsTool.Parameters("project-1", "deployment", 20, null), context);

        assertThat(response.total()).isEqualTo(21);
        assertThat(response.hasMore()).isTrue();
        verify(workItemRepository, never()).findPageForProject("project-1", null, null, null, null, 20, 0L);
    }

    @Test
    void fetchEntriesUsesPagedServiceReadAndReportsMorePages() {
        Entry entry = new Entry();
        entry.setId("entry-1");
        when(entryService.listPageForTool("project-1", "item-1", 10, 10L)).thenReturn(List.of(entry));
        when(entryService.countForTool("project-1", "item-1")).thenReturn(12L);

        FetchEntriesTool.Response response = (FetchEntriesTool.Response) new FetchEntriesTool(entryService, authorization)
                .execute(new FetchEntriesTool.Parameters("project-1", "item-1", 10, 10), context);

        assertThat(response.entries()).containsExactly(entry);
        assertThat(response.total()).isEqualTo(12);
        assertThat(response.hasMore()).isTrue();
        verify(entryService).listPageForTool("project-1", "item-1", 10, 10L);
        verify(entryService).countForTool("project-1", "item-1");
        verify(entryService, never()).list("project-1");
    }

    @Test
    void fetchRelationshipsUsesTargetedPagedEntityRead() {
        Relationship relationship = new Relationship();
        relationship.setId("relationship-1");
        when(relationshipService.listPageForTool("project-1", "item-1", 50, 0L)).thenReturn(List.of(relationship));
        when(relationshipService.countForTool("project-1", "item-1")).thenReturn(1L);

        FetchRelationshipsTool.Response response = (FetchRelationshipsTool.Response) new FetchRelationshipsTool(relationshipService, authorization)
                .execute(new FetchRelationshipsTool.Parameters("project-1", "item-1", null, null), context);

        assertThat(response.relationships()).containsExactly(relationship);
        assertThat(response.total()).isOne();
        assertThat(response.hasMore()).isFalse();
        verify(relationshipService).listPageForTool("project-1", "item-1", 50, 0L);
        verify(relationshipService, never()).list("project-1");
    }

    @Test
    void fetchProjectBlockersIsBoundedAndReportsTotal() {
        RelationshipRepository.BlockerRow row = new RelationshipRepository.BlockerRow(
                "relationship-1", "blocked-1", "Blocked", "OPEN", "blocker-1", "Blocker", "OPEN", "reason");
        when(relationshipRepository.findPageWorkItemBlockers("project-1", 50, 50L)).thenReturn(List.of(row));
        when(relationshipRepository.countAllWorkItemBlockers("project-1")).thenReturn(101L);

        FetchProjectBlockersTool.Response response = (FetchProjectBlockersTool.Response) new FetchProjectBlockersTool(relationshipRepository, authorization)
                .execute(new FetchProjectBlockersTool.Parameters("project-1", 50, 50), context);

        assertThat(response.count()).isOne();
        assertThat(response.total()).isEqualTo(101);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void fetchTeamMembersUsesDatabasePageAndCompletenessMetadata() {
        Team team = team("team-1", "SRE");
        TeamMember membership = new TeamMember();
        membership.setTeamId("team-1");
        membership.setUserId("user-1");
        membership.setRole("MEMBER");
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setDisplayName("Alex");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(teamMemberRepository.findPageByTeamId("team-1", 20, 20L)).thenReturn(List.of(membership));
        when(teamMemberRepository.countByTeamId("team-1")).thenReturn(21L);
        when(appUserRepository.findActiveUsersByIds(List.of("user-1"))).thenReturn(List.of(user));

        FetchTeamMembersTool.Result result = (FetchTeamMembersTool.Result) new FetchTeamMembersTool(
                teamRepository, teamMemberRepository, appUserRepository, authorization)
                .execute(new FetchTeamMembersTool.Parameters("team-1", null, 20), context);

        assertThat(result.total()).isEqualTo(21);
        assertThat(result.hasMore()).isFalse();
        verify(teamMemberRepository, never()).findByTeamId("team-1");
    }

    @Test
    void fetchTeamProjectsUsesDatabasePageAndCompletenessMetadata() {
        Team team = team("team-1", "SRE");
        ProjectTeam link = new ProjectTeam();
        link.setTeamId("team-1");
        link.setProjectId("project-1");
        link.setRole("OWNER");
        Project project = new Project();
        project.setId("project-1");
        project.setName("Platform");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(projectTeamRepository.findPageByTeamId("team-1", 20, 0L)).thenReturn(List.of(link));
        when(projectTeamRepository.countByTeamId("team-1")).thenReturn(1L);
        when(projectRepository.findAllById(List.of("project-1"))).thenReturn(List.of(project));

        FetchTeamProjectsTool.Result result = (FetchTeamProjectsTool.Result) new FetchTeamProjectsTool(
                teamRepository, projectTeamRepository, projectRepository, authorization)
                .execute(new FetchTeamProjectsTool.Parameters("team-1", null, null), context);

        assertThat(result.total()).isOne();
        assertThat(result.hasMore()).isFalse();
        verify(projectTeamRepository, never()).findByTeamId("team-1");
    }

    @Test
    void fetchProjectAssigneesReturnsOnlyProjectScopedCandidates() {
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setUsername("kc");
        user.setDisplayName("KC");
        Team team = team("team-1", "Platform");
        when(appUserRepository.findAssignableUsersForProject("project-1", "kc", 10))
                .thenReturn(List.of(user));
        when(teamRepository.findAssignableTeamsForProject("project-1", "kc", 10))
                .thenReturn(List.of(team));

        FetchProjectAssigneesTool.Result result = (FetchProjectAssigneesTool.Result) new FetchProjectAssigneesTool(
                appUserRepository, teamRepository, authorization)
                .execute(new FetchProjectAssigneesTool.Parameters("project-1", " kc ", 10), context);

        assertThat(result.projectId()).isEqualTo("project-1");
        assertThat(result.users()).singleElement().satisfies(candidate ->
                assertThat(candidate.id()).isEqualTo("user-1"));
        assertThat(result.teams()).singleElement().satisfies(candidate ->
                assertThat(candidate.id()).isEqualTo("team-1"));
        assertThat(result.userCount()).isOne();
        assertThat(result.teamCount()).isOne();
        verify(appUserRepository).findAssignableUsersForProject("project-1", "kc", 10);
        verify(teamRepository).findAssignableTeamsForProject("project-1", "kc", 10);
    }

    @Test
    void projectSummaryKeepsAggregationServerSide() {
        when(workItemRepository.countAllByProjectId("project-1")).thenReturn(1000L);
        when(entryRepository.countAllByProjectId("project-1")).thenReturn(2000L);
        when(relationshipRepository.countAllByProjectId("project-1")).thenReturn(300L);
        when(relationshipRepository.countWorkItemBlockers("project-1")).thenReturn(12L);
        when(relationshipRepository.countBlockedWorkItems("project-1")).thenReturn(9L);
        when(workItemRepository.countByStatusForProject("project-1")).thenReturn(List.of());
        when(workItemRepository.countByTypeForProject("project-1")).thenReturn(List.of());
        when(workItemRepository.countByPriorityForProject("project-1")).thenReturn(List.of());
        when(entryRepository.countByTypeForProject("project-1")).thenReturn(List.of());
        when(relationshipRepository.countByTypeForProject("project-1")).thenReturn(List.of());
        when(workItemRepository.summarizeDueDates("project-1")).thenReturn(new WorkItemRepository.DueDateSummary(0, 0, 0));
        when(workItemAssigneeRepository.countByProjectId("project-1")).thenReturn(List.of());

        FetchProjectSummaryTool.Response response = (FetchProjectSummaryTool.Response) new FetchProjectSummaryTool(
                workItemRepository, workItemAssigneeRepository, entryRepository, relationshipRepository, authorization)
                .execute(new FetchProjectSummaryTool.Parameters("project-1"), context);

        assertThat(response.totals().workItems()).isEqualTo(1000);
        assertThat(response.totals().entries()).isEqualTo(2000);
        assertThat(response.totals().blockers()).isEqualTo(12);
        verify(workItemRepository, never()).findByProjectId("project-1");
        verify(entryRepository, never()).findByProjectId("project-1");
        verify(relationshipRepository, never()).findByProjectId("project-1");
    }

    private static WorkItem workItem(String id) {
        WorkItem item = new WorkItem();
        item.setId(id);
        item.setProjectId("project-1");
        item.setTitle("Work item");
        return item;
    }

    private static Team team(String id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private static ToolExecutionContext context() {
        AppUser actor = new AppUser();
        actor.setId("admin-1");
        actor.setGlobalRole("ADMIN");
        return new ToolExecutionContext(actor, "session-1", List.of("project-1"));
    }
}
