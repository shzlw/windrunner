package com.windrunner.server.project;

import com.windrunner.server.chat.persistence.ChatSessionContextRepository;
import com.windrunner.server.notification.persistence.UserNotificationRepository;
import com.windrunner.server.subscription.persistence.SubscriptionRepository;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes operational data owned by a project. Audit and LLM usage history are
 * intentionally retained for administration and reporting.
 */
@Service
@RequiredArgsConstructor
public class ProjectContentDeletionService {

    private final WorkspaceChangeRepository workspaceChanges;
    private final WorkspaceChangeProposalRepository workspaceChangeProposals;
    private final WorkItemAssigneeRepository workItemAssignees;
    private final RelationshipRepository relationships;
    private final EntryRepository entries;
    private final SubscriptionRepository subscriptions;
    private final UserNotificationRepository notifications;
    private final ChatSessionContextRepository chatContexts;
    private final WorkItemRepository workItems;

    @Transactional
    public void deleteProjectContent(String projectId) {
        workspaceChanges.deleteByProjectId(projectId);
        workspaceChangeProposals.deleteByProjectId(projectId);
        workItemAssignees.deleteByProjectId(projectId);
        relationships.deleteByProjectId(projectId);
        entries.deleteByProjectId(projectId);
        subscriptions.deleteByProjectId(projectId);
        notifications.deleteByProjectId(projectId);
        chatContexts.deleteByEntity("PROJECT", projectId);
        workItems.deleteByProjectId(projectId);
    }
}
