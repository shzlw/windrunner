package com.windrunner.server.mail;

import com.windrunner.server.mail.domain.MailNotification;
import com.windrunner.server.mail.persistence.MailNotificationRepository;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailNotificationDeliveryService {
    private final MailProperties properties;
    private final MailService mail;
    private final MailNotificationRepository queue;
    private final AppUserRepository users;
    private final WorkItemRepository workItems;
    private final WorkItemAssigneeRepository assignees;
    private final TeamMemberRepository teamMembers;
    private final TeamRepository teams;
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final TransactionTemplate transactions;

    @PostConstruct
    public void validateConfiguration() {
        if (!properties.isEnabled()) return;
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalStateException("Mail is enabled but windrunner.mail.base-url is not configured");
        URI uri = URI.create(baseUrl);
        if (!List.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null)
            throw new IllegalStateException("windrunner.mail.base-url must be an absolute HTTP(S) application URL");
    }

    @Scheduled(fixedDelay = 15000)
    public void deliverReady() {
        if (!properties.isEnabled()) return;
        for (String userId : queue.findReadyRecipients()) {
            try {
                transactions.executeWithoutResult(status -> deliverRecipient(userId));
            } catch (RuntimeException exception) {
                log.warn("Mail queue processing failed for recipient {}", userId);
            }
        }
    }

    @Scheduled(fixedDelay = 86400000)
    public void deleteExpired() {
        queue.deleteExpired();
    }

    private void deliverRecipient(String userId) {
        if (queue.lockRecipient(userId).isEmpty()) return;
        List<MailNotification> pending = queue.findReadyForUpdate(userId);
        AppUser user = users.findById(userId).orElse(null);
        Map<String, String> assignmentBodies = new LinkedHashMap<>();
        List<MailNotification> assignmentRows = new ArrayList<>();
        for (MailNotification notification : pending) {
            if (notification.workItemId() == null) {
                send(List.of(notification), notification.recipientEmail(), "Windrunner account update", notification.body());
            } else {
                String body = buildAssignment(notification, user);
                if (body == null) queue.deleteById(notification.id());
                else {
                    assignmentRows.add(notification);
                    assignmentBodies.put(notification.workItemId(), body);
                }
            }
        }
        if (!assignmentRows.isEmpty()) {
            send(assignmentRows, user.getEmail(), "Windrunner: work assigned to you",
                    String.join("\n\n", assignmentBodies.values()));
        }
    }

    private String buildAssignment(MailNotification notification, AppUser user) {
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || !Objects.equals(user.getEmail(), notification.recipientEmail())) return null;
        var item = workItems.findById(notification.workItemId()).orElse(null);
        if (item == null || !access.hasProjectRole(item.getProjectId(), user.getId(), ProjectRoles.VIEWER)) return null;
        List<String> assignedThrough = new ArrayList<>();
        for (var assignee : assignees.findByWorkItemId(item.getId())) {
            if ("USER".equals(assignee.getAssigneeType()) && user.getId().equals(assignee.getAssigneeId()))
                assignedThrough.add("you directly");
            if ("TEAM".equals(assignee.getAssigneeType())
                    && teamMembers.findByTeamIdAndUserId(assignee.getAssigneeId(), user.getId()).isPresent())
                teams.findById(assignee.getAssigneeId()).ifPresent(team -> assignedThrough.add("team " + team.getName()));
        }
        if (assignedThrough.isEmpty()) return null;
        String project = projects.findById(item.getProjectId()).map(p -> p.getName()).orElse(null);
        if (project == null) return null;
        String actor = users.findById(notification.actorUserId()).map(AppUser::getUsername).orElse("Someone");
        return project + " — " + item.getTitle() + "\nAssigned by " + actor + " to "
                + String.join(", ", assignedThrough)
                + (item.getDueDate() == null ? "" : "\nDue: " + item.getDueDate())
                + "\n" + properties.getBaseUrl().replaceAll("/+$", "") + "/projects/"
                + URLEncoder.encode(item.getProjectId(), StandardCharsets.UTF_8)
                + "?workItemId=" + URLEncoder.encode(item.getId(), StandardCharsets.UTF_8);
    }

    private void send(List<MailNotification> rows, String email, String subject, String body) {
        try {
            mail.sendText(email, subject, body, null);
        } catch (RuntimeException exception) {
            rows.forEach(row -> queue.retry(row.id()));
            log.warn("Mail delivery failed for queue item {}; retry limit is three attempts", rows.getFirst().id());
            return;
        }
        rows.forEach(row -> queue.deleteById(row.id()));
    }
}
