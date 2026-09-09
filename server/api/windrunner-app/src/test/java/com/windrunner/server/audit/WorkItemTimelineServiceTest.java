package com.windrunner.server.audit;

import com.windrunner.server.audit.persistence.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemTimelineServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void derivesLifecycleMilestonesFromWorkItemAuditSnapshots() {
        when(auditLogRepository.findWorkItemTimeline("project-1", "work-1", 200)).thenReturn(List.of(
                new AuditLogRepository.WorkItemTimelineRow(
                        "audit-1",
                        OffsetDateTime.parse("2026-06-01T09:00:00Z"),
                        "user-1",
                        "CREATE",
                        null,
                        "{\"status\":\"OPEN\",\"dueDate\":null,\"assignees\":[{\"assigneeType\":\"TEAM\",\"assigneeId\":\"team-1\"}]}",
                        null),
                new AuditLogRepository.WorkItemTimelineRow(
                        "audit-2",
                        OffsetDateTime.parse("2026-06-02T09:00:00Z"),
                        "user-1",
                        "UPDATE",
                        "{\"status\":\"OPEN\",\"dueDate\":null,\"assignees\":[{\"assigneeType\":\"TEAM\",\"assigneeId\":\"team-1\"}]}",
                        "{\"status\":\"IN_PROGRESS\",\"dueDate\":\"2026-06-10\",\"assignees\":[{\"assigneeType\":\"USER\",\"assigneeId\":\"user-2\"}]}",
                        "{\"status\":{\"from\":\"OPEN\",\"to\":\"IN_PROGRESS\"},\"assignees\":{\"from\":[],\"to\":[]},\"dueDate\":{\"from\":null,\"to\":\"2026-06-10\"}}")));

        var events = new WorkItemTimelineService(auditLogRepository).list("project-1", "work-1");

        assertThat(events).extracting("kind").containsExactly("CREATED", "STARTED", "ASSIGNMENT_CHANGED", "DUE_DATE_CHANGED");
        assertThat(events.getFirst().addedAssignees()).singleElement().satisfies(assignee -> {
            assertThat(assignee.assigneeType()).isEqualTo("TEAM");
            assertThat(assignee.assigneeId()).isEqualTo("team-1");
        });
        assertThat(events.get(1).toStatus()).isEqualTo("IN_PROGRESS");
        assertThat(events.get(2).addedAssignees()).singleElement().satisfies(assignee -> {
            assertThat(assignee.assigneeType()).isEqualTo("USER");
            assertThat(assignee.assigneeId()).isEqualTo("user-2");
        });
        assertThat(events.get(3).toDueDate()).isEqualTo("2026-06-10");
    }

    @Test
    void returnsLatestEventsNewestFirst() {
        when(auditLogRepository.findWorkItemTimeline("project-1", "work-1", 200)).thenReturn(List.of(
                new AuditLogRepository.WorkItemTimelineRow(
                        "audit-1", OffsetDateTime.parse("2026-06-01T09:00:00Z"), "user-1", "CREATE",
                        null, "{\"status\":\"OPEN\"}", null),
                new AuditLogRepository.WorkItemTimelineRow(
                        "audit-2", OffsetDateTime.parse("2026-06-02T09:00:00Z"), "user-1", "UPDATE",
                        "{\"status\":\"OPEN\",\"dueDate\":null}", "{\"status\":\"IN_PROGRESS\",\"dueDate\":\"2026-06-10\"}",
                        "{\"status\":{\"from\":\"OPEN\",\"to\":\"IN_PROGRESS\"},\"dueDate\":{\"from\":null,\"to\":\"2026-06-10\"}}")));

        var latest = new WorkItemTimelineService(auditLogRepository).listLatest("project-1", "work-1", 2);

        assertThat(latest).extracting("kind").containsExactly("DUE_DATE_CHANGED", "STARTED");
    }
}
