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
        when(workItemRepository.findCalendarAssignmentsByUserIds(List.of("project-1"), List.of("actor"), 500))
                .thenReturn(List.of(new WorkItemRepository.CalendarAssignmentRow(
                        "work-1", "project-1", "Platform", "API migration", "BLOCKED",
                        LocalDate.of(2026, 6, 10), "USER", "actor")));
        when(auditLogRepository.findWorkItemStatusHistory(List.of("work-1"))).thenReturn(List.of(
                new AuditLogRepository.WorkItemStatusRow(
                        "work-1", OffsetDateTime.parse("2026-06-02T12:00:00Z"), "IN_PROGRESS")));

        var items = calendarEventService.listWorkItems(actor, "USER", "actor", rangeStart(), rangeEnd());

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().startedOn()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(items.getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(items.getFirst().status()).isEqualTo("BLOCKED");
        assertThat(items.getFirst().overdue()).isTrue();
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
