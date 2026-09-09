package com.windrunner.server.attention;

import com.windrunner.server.attention.api.AttentionSummaryView;
import com.windrunner.server.attention.api.AttentionWorkItemView;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.notification.NotificationService;
import com.windrunner.server.notification.api.NotificationPage;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttentionService {

    private static final int SECTION_LIMIT = 5;
    private static final int DUE_SOON_DAYS = 7;
    private static final int NEW_ASSIGNMENT_DAYS = 7;

    private final WorkItemRepository workItems;
    private final ProjectRepository projects;
    private final NotificationService notifications;

    public AttentionSummaryView getSummary(AppUser actor) {
        List<String> projectIds = findVisibleProjectIds(actor);
        List<WorkItemRepository.AttentionAssignmentRow> assignments = List.of();
        if (!projectIds.isEmpty()) {
            ZoneId zone = resolveZone(actor.getTimezone());
            LocalDate today = LocalDate.now(zone);
            assignments = workItems.findAttentionAssignments(
                    actor.getId(), projectIds, today, today.plusDays(DUE_SOON_DAYS),
                    OffsetDateTime.now(zone).minusDays(NEW_ASSIGNMENT_DAYS), SECTION_LIMIT + 1);
        }

        NotificationPage unread = notifications.listForUser(actor.getId(), true, SECTION_LIMIT + 1, 0);
        return new AttentionSummaryView(
                buildCategoryItems(assignments, "OVERDUE"), hasMoreCategoryItems(assignments, "OVERDUE"),
                buildCategoryItems(assignments, "BLOCKED"), hasMoreCategoryItems(assignments, "BLOCKED"),
                buildCategoryItems(assignments, "DUE_SOON"), hasMoreCategoryItems(assignments, "DUE_SOON"),
                buildCategoryItems(assignments, "NEW"), hasMoreCategoryItems(assignments, "NEW"),
                unread.items().stream().limit(SECTION_LIMIT).toList(), unread.items().size() > SECTION_LIMIT);
    }

    private List<AttentionWorkItemView> buildCategoryItems(
            List<WorkItemRepository.AttentionAssignmentRow> rows,
            String category) {
        return rows.stream()
                .filter(row -> category.equals(row.category()))
                .limit(SECTION_LIMIT)
                .map(row -> new AttentionWorkItemView(
                        row.projectId(), row.projectName(), row.workItemId(), row.title(), row.type(), row.status(),
                        row.dueDate(), row.priority(), row.assignedAt(), row.blocked()))
                .toList();
    }

    private boolean hasMoreCategoryItems(List<WorkItemRepository.AttentionAssignmentRow> rows, String category) {
        return rows.stream().filter(row -> category.equals(row.category())).count() > SECTION_LIMIT;
    }

    private List<String> findVisibleProjectIds(AppUser actor) {
        List<Project> visible = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projects.findAllByOrderByNameAscIdAsc()
                : projects.findVisibleToUser(actor.getId());
        return visible.stream().map(Project::getId).toList();
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return timezone == null || timezone.isBlank() ? ZoneId.of("UTC") : ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            return ZoneId.of("UTC");
        }
    }
}
