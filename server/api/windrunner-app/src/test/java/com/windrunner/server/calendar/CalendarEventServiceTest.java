package com.windrunner.server.calendar;

import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.calendar.persistence.CalendarEventRepository;
import com.windrunner.server.calendar.domain.CalendarEvent;
import com.windrunner.server.calendar.api.CalendarEventRequest;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    private CalendarEventService calendarEventService;

    @BeforeEach
    void setUp() {
        calendarEventService = new CalendarEventService(
                calendarEventRepository,
                auditLogRepository,
                appUserRepository,
                workItemRepository,
                projectMemberRepository,
                projectAccessService,
                projectRepository,
                teamRepository,
                teamMemberRepository,
                new EntityIdGenerator());
    }

    @Test
    void createsEventWithTitleAndDatesWithoutCategoryOrBusyFlag() {
        AppUser actor = user("actor");
        when(appUserRepository.findById("actor")).thenReturn(Optional.of(actor));
        when(calendarEventRepository.insert(anyString(), eq("actor"), eq("Vacation"), isNull(),
                eq(rangeStart()), eq(rangeEnd()), eq("UTC"), eq(true), isNull(), eq("actor"), any(), any()))
                .thenReturn(1);

        var event = calendarEventService.createEvent(new CalendarEventRequest(
                "actor", "Vacation", null, rangeStart(), rangeEnd(), "UTC", true, null), actor);

        assertThat(event.title()).isEqualTo("Vacation");
        assertThat(event.allDay()).isTrue();
        assertThat(event.startsAt()).isEqualTo(rangeStart());
        assertThat(event.endsAt()).isEqualTo(rangeEnd());
        assertThat(event.createdByUserId()).isEqualTo("actor");
        assertThat(event.createdAt()).isNotNull();
        assertThat(event.updatedAt()).isEqualTo(event.createdAt());
    }

    @Test
    void sharedTeamMemberCanReadAnotherUsersCalendar() {
        AppUser actor = user("actor");
        when(appUserRepository.findById("target")).thenReturn(Optional.of(user("target")));
        when(teamMemberRepository.areUsersInSharedTeam("actor", "target")).thenReturn(true);
        when(calendarEventRepository.findByUserIdsAndRange(eq(List.of("target")), any(), any())).thenReturn(List.of());

        assertThat(calendarEventService.listEvents(actor, "USER", "target", rangeStart(), rangeEnd())).isEmpty();

        verify(calendarEventRepository).findByUserIdsAndRange(eq(List.of("target")), any(), any());
    }

    @Test
    void listsAssignedWorkItemFromLatestInProgressTransitionUntilDueDate() {
        AppUser actor = user("actor");
        when(appUserRepository.findById("actor")).thenReturn(Optional.of(actor));
        var project = new Project();
        project.setId("project-1");
        project.setName("Platform");
        when(projectRepository.findVisibleToUser("actor")).thenReturn(List.of(project));
        when(workItemRepository.findCalendarAssignmentsByUserIds(List.of("project-1"), List.of("actor"), 501))
                .thenReturn(List.of(new WorkItemRepository.CalendarAssignmentRow(
                        "work-1", "project-1", "Platform", "API migration", "BLOCKED",
                        LocalDate.of(2026, 6, 10), "USER", "actor")));
        when(auditLogRepository.findWorkItemStatusHistory(List.of("work-1"))).thenReturn(List.of(
                new AuditLogRepository.WorkItemStatusRow(
                        "work-1", OffsetDateTime.parse("2026-06-02T12:00:00Z"), "IN_PROGRESS")));

        var result = calendarEventService.listWorkItems(actor, "USER", "actor", rangeStart(), rangeEnd());

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().startedOn()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(result.items().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(result.items().getFirst().status()).isEqualTo("BLOCKED");
        assertThat(result.items().getFirst().overdue()).isTrue();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void excludesWorkItemStartingAtExclusiveRangeEnd() {
        AppUser actor = user("actor");
        when(appUserRepository.findById("actor")).thenReturn(Optional.of(actor));
        Project project = new Project();
        project.setId("project-1");
        when(projectRepository.findVisibleToUser("actor")).thenReturn(List.of(project));
        when(workItemRepository.findCalendarAssignmentsByUserIds(List.of("project-1"), List.of("actor"), 501))
                .thenReturn(List.of(new WorkItemRepository.CalendarAssignmentRow(
                        "work-1", "project-1", "Platform", "July work", "IN_PROGRESS",
                        LocalDate.of(2026, 7, 10), "USER", "actor")));
        when(auditLogRepository.findWorkItemStatusHistory(List.of("work-1"))).thenReturn(List.of(
                new AuditLogRepository.WorkItemStatusRow(
                        "work-1", OffsetDateTime.parse("2026-07-01T00:00:00Z"), "IN_PROGRESS")));

        var result = calendarEventService.listWorkItems(actor, "USER", "actor", rangeStart(), rangeEnd());

        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void userOutsideSharedTeamCannotReadAnotherUsersCalendar() {
        AppUser actor = user("actor");
        when(appUserRepository.findById("target")).thenReturn(Optional.of(user("target")));
        when(teamMemberRepository.areUsersInSharedTeam("actor", "target")).thenReturn(false);

        assertThatThrownBy(() -> calendarEventService.listEvents(actor, "USER", "target", rangeStart(), rangeEnd()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void teamCalendarRequiresTeamMembership() {
        AppUser actor = user("actor");
        Team team = new Team();
        team.setId("team-1");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId("team-1", "actor")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarEventService.listEvents(actor, "TEAM", "team-1", rangeStart(), rangeEnd()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void sharedTeamMemberStillCannotWriteAnotherUsersCalendar() {
        AppUser actor = user("actor");
        CalendarEvent event = new CalendarEvent();
        event.setId("event-1");
        event.setUserId("target");
        when(calendarEventRepository.findById("event-1")).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> calendarEventService.deleteEvent("event-1", actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(calendarEventRepository, never()).delete(anyString(), anyString());
    }

    @Test
    void summarizesTeamWorkloadWithoutAttributingTeamAssignmentsToEveryMember() {
        AppUser actor = user("actor");
        Team team = new Team();
        team.setId("team-1");
        team.setName("Platform");
        TeamMember membership = new TeamMember();
        membership.setTeamId("team-1");
        membership.setUserId("actor");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId("team-1", "actor")).thenReturn(Optional.of(membership));
        when(teamMemberRepository.findByTeamId("team-1")).thenReturn(List.of(membership));
        when(appUserRepository.findAllById(List.of("actor"))).thenReturn(List.of(actor));
        Project project = new Project();
        project.setId("project-1");
        project.setName("Platform");
        when(projectRepository.findVisibleToUser("actor")).thenReturn(List.of(project));
        when(workItemRepository.findCalendarAssignmentsForTeam(List.of("project-1"), List.of("actor"), "team-1", 501))
                .thenReturn(List.of(
                        new WorkItemRepository.CalendarAssignmentRow(
                                "work-1", "project-1", "Platform", "API migration", "IN_PROGRESS",
                                LocalDate.of(2026, 6, 10), "USER", "actor"),
                        new WorkItemRepository.CalendarAssignmentRow(
                                "work-2", "project-1", "Platform", "Team backlog", "OPEN",
                                null, "TEAM", "team-1")));
        when(auditLogRepository.findWorkItemStatusHistory(List.of("work-1", "work-2"))).thenReturn(List.of(
                new AuditLogRepository.WorkItemStatusRow(
                        "work-1", OffsetDateTime.parse("2026-06-02T12:00:00Z"), "IN_PROGRESS")));
        CalendarEvent event = new CalendarEvent();
        event.setId("event-1");
        event.setUserId("actor");
        event.setTitle("Planning");
        event.setStartsAt(OffsetDateTime.parse("2026-06-03T09:00:00Z"));
        event.setEndsAt(OffsetDateTime.parse("2026-06-03T10:00:00Z"));
        event.setTimezone("UTC");
        when(calendarEventRepository.findByUserIdsAndRange(eq(List.of("actor")), any(), any())).thenReturn(List.of(event));

        var workload = calendarEventService.getTeamWorkload(
                actor, "team-1", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        assertThat(workload.members()).singleElement().satisfies(member -> {
            assertThat(member.scheduledWorkItemCount()).isEqualTo(1);
            assertThat(member.calendarEventCount()).isEqualTo(1);
            assertThat(member.calendarEventMinutes()).isEqualTo(60);
            assertThat(member.unplannedWorkItemCount()).isZero();
        });
        assertThat(workload.workItemsTruncated()).isFalse();
        assertThat(workload.teamAssignedWorkItemCount()).isEqualTo(1);
        assertThat(workload.teamAssignedWorkItems()).extracting("workItemId").containsExactly("work-2");
    }

    @Test
    void reportsWhenTeamWorkItemsAreTruncated() {
        AppUser actor = user("actor");
        Team team = new Team();
        team.setId("team-1");
        team.setName("Platform");
        TeamMember membership = new TeamMember();
        membership.setTeamId("team-1");
        membership.setUserId("actor");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId("team-1", "actor")).thenReturn(Optional.of(membership));
        when(teamMemberRepository.findByTeamId("team-1")).thenReturn(List.of(membership));
        when(appUserRepository.findAllById(List.of("actor"))).thenReturn(List.of(actor));
        Project project = new Project();
        project.setId("project-1");
        when(projectRepository.findVisibleToUser("actor")).thenReturn(List.of(project));
        List<WorkItemRepository.CalendarAssignmentRow> assignments = IntStream.range(0, 501)
                .mapToObj(index -> new WorkItemRepository.CalendarAssignmentRow(
                        "work-" + index, "project-1", "Platform", "Work " + index, "OPEN",
                        null, "USER", "actor"))
                .toList();
        when(workItemRepository.findCalendarAssignmentsForTeam(
                List.of("project-1"), List.of("actor"), "team-1", 501)).thenReturn(assignments);

        var workload = calendarEventService.getTeamWorkload(
                actor, "team-1", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        assertThat(workload.workItemsTruncated()).isTrue();
        assertThat(workload.members()).singleElement().satisfies(member ->
                assertThat(member.unplannedWorkItemCount()).isEqualTo(500));

        var result = calendarEventService.listWorkItems(
                actor, "TEAM", "team-1",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T00:00:00Z"));

        assertThat(result.items()).hasSize(500);
        assertThat(result.truncated()).isTrue();
    }

    private AppUser user(String id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(id);
        user.setGlobalRole("USER");
        user.setTimezone("UTC");
        return user;
    }

    private OffsetDateTime rangeStart() {
        return OffsetDateTime.parse("2026-06-01T00:00:00Z");
    }

    private OffsetDateTime rangeEnd() {
        return OffsetDateTime.parse("2026-07-01T00:00:00Z");
    }
}
