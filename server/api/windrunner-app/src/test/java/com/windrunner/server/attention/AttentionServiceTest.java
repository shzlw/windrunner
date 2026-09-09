package com.windrunner.server.attention;

import com.windrunner.server.notification.NotificationService;
import com.windrunner.server.notification.api.NotificationPage;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.persistence.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttentionServiceTest {

    @Mock
    private WorkItemRepository workItems;
    @Mock
    private ProjectRepository projects;
    @Mock
    private NotificationService notifications;

    @Test
    void returnsBoundedAttentionSectionsForTheAuthenticatedUser() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        actor.setGlobalRole("USER");
        actor.setTimezone("UTC");
        Project project = new Project();
        project.setId("project-1");
        when(projects.findVisibleToUser("user-1")).thenReturn(List.of(project));
        when(workItems.findAttentionAssignments(eq("user-1"), eq(List.of("project-1")), any(), any(), any(), eq(6)))
                .thenReturn(List.of(
                        row("OVERDUE", "overdue-1"),
                        row("BLOCKED", "blocked-1"),
                        row("DUE_SOON", "due-1"),
                        row("NEW", "new-1")));
        when(notifications.listForUser("user-1", true, 6, 0))
                .thenReturn(new NotificationPage(List.of(), 0, 0));

        var summary = new AttentionService(workItems, projects, notifications).getSummary(actor);

        assertThat(summary.overdueAssignments()).extracting("workItemId").containsExactly("overdue-1");
        assertThat(summary.blockedAssignments()).extracting("workItemId").containsExactly("blocked-1");
        assertThat(summary.dueSoonWork()).extracting("workItemId").containsExactly("due-1");
        assertThat(summary.newAssignments()).extracting("workItemId").containsExactly("new-1");
    }

    private WorkItemRepository.AttentionAssignmentRow row(String category, String workItemId) {
        return new WorkItemRepository.AttentionAssignmentRow(
                category, "project-1", "Platform", workItemId, workItemId, "TASK", "OPEN",
                LocalDate.now().plusDays(1), "HIGH", OffsetDateTime.now().minusDays(1), "BLOCKED".equals(category));
    }
}
