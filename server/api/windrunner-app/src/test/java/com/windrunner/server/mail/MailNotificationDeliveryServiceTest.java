package com.windrunner.server.mail;

import com.windrunner.server.mail.domain.MailNotification;
import com.windrunner.server.mail.persistence.MailNotificationRepository;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailNotificationDeliveryServiceTest {
    private final MailProperties properties = new MailProperties();
    private final MailService mail = mock(MailService.class);
    private final MailNotificationRepository queue = mock(MailNotificationRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final WorkItemRepository items = mock(WorkItemRepository.class);
    private final WorkItemAssigneeRepository assignees = mock(WorkItemAssigneeRepository.class);
    private final TeamMemberRepository members = mock(TeamMemberRepository.class);
    private final TeamRepository teams = mock(TeamRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final MailNotificationDeliveryService service = new MailNotificationDeliveryService(
            properties, mail, queue, users, items, assignees, members, teams, projects, access, transactions);

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        properties.setBaseUrl("https://app.example.com");
        doAnswer(call -> {
            Consumer<SimpleTransactionStatus> action = call.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(queue.findReadyRecipients()).thenReturn(List.of("user"));
        when(queue.lockRecipient("user")).thenReturn(List.of("user"));
        AppUser user = new AppUser();
        user.setId("user");
        user.setEmail("user@example.com");
        user.setStatus("ACTIVE");
        when(users.findById("user")).thenReturn(Optional.of(user));
    }

    @Test
    void batchesDirectAndTeamAssignmentsInOneEmail() {
        prepareItem("one", "USER", "user");
        prepareItem("two", "TEAM", "team");
        Team team = new Team();
        team.setName("Engineering");
        when(teams.findById("team")).thenReturn(Optional.of(team));
        when(members.findByTeamIdAndUserId("team", "user")).thenReturn(Optional.of(new TeamMember()));
        when(queue.findReadyForUpdate("user")).thenReturn(List.of(row("a", "one"), row("b", "one"), row("c", "two")));
        service.deliverReady();
        verify(mail).sendText(eq("user@example.com"), anyString(),
                argThat(body -> body.contains("one") && body.contains("two")
                        && body.contains("team Engineering") && body.indexOf("Project — one") == body.lastIndexOf("Project — one")), isNull());
        verify(queue).deleteById("a");
        verify(queue).deleteById("b");
        verify(queue).deleteById("c");
    }

    @Test
    void suppressesRevokedAccessAndRemovedAssignments() {
        prepareItem("one", "USER", "user");
        when(access.hasProjectRole("project", "user", ProjectRoles.VIEWER)).thenReturn(false);
        when(queue.findReadyForUpdate("user")).thenReturn(List.of(row("a", "one")));
        service.deliverReady();
        verifyNoInteractions(mail);
        verify(queue).deleteById("a");
    }

    @Test
    void suppressesRemovedAssignment() {
        prepareItem("one", "USER", "someoneElse");
        when(queue.findReadyForUpdate("user")).thenReturn(List.of(row("a", "one")));
        service.deliverReady();
        verifyNoInteractions(mail);
        verify(queue).deleteById("a");
    }

    @Test
    void retriesFailedMailWithoutDeletingQueueRows() {
        MailNotification notice = new MailNotification("a", "user", "old@example.com", null, null, "Account changed", 0);
        when(queue.findReadyForUpdate("user")).thenReturn(List.of(notice));
        doThrow(new IllegalStateException("SMTP unavailable")).when(mail).sendText(anyString(), anyString(), anyString(), isNull());
        service.deliverReady();
        verify(queue).retry("a");
        verify(queue, never()).deleteById(anyString());
    }

    private MailNotification row(String id, String item) {
        return new MailNotification(id, "user", "user@example.com", item, "actor", null, 0);
    }

    private void prepareItem(String id, String type, String assigneeId) {
        WorkItem item = new WorkItem();
        item.setId(id);
        item.setTitle(id);
        item.setProjectId("project");
        when(items.findById(id)).thenReturn(Optional.of(item));
        when(access.hasProjectRole("project", "user", ProjectRoles.VIEWER)).thenReturn(true);
        WorkItemAssignee assignee = new WorkItemAssignee();
        assignee.setAssigneeType(type);
        assignee.setAssigneeId(assigneeId);
        when(assignees.findByWorkItemId(id)).thenReturn(List.of(assignee));
        Project project = new Project();
        project.setName("Project");
        when(projects.findById("project")).thenReturn(Optional.of(project));
    }
}
