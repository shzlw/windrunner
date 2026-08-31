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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectContentDeletionServiceTest {

    @Mock private WorkspaceChangeRepository workspaceChanges;
    @Mock private WorkspaceChangeProposalRepository proposals;
    @Mock private WorkItemAssigneeRepository assignees;
    @Mock private RelationshipRepository relationships;
    @Mock private EntryRepository entries;
    @Mock private SubscriptionRepository subscriptions;
    @Mock private UserNotificationRepository notifications;
    @Mock private ChatSessionContextRepository contexts;
    @Mock private WorkItemRepository workItems;

    @Test
    void deletesProjectOwnedOperationalDataButNotHistoryTables() {
        new ProjectContentDeletionService(workspaceChanges, proposals, assignees, relationships, entries,
                subscriptions, notifications, contexts, workItems).deleteProjectContent("proj-1");

        verify(workspaceChanges).deleteByProjectId("proj-1");
        verify(proposals).deleteByProjectId("proj-1");
        verify(assignees).deleteByProjectId("proj-1");
        verify(relationships).deleteByProjectId("proj-1");
        verify(entries).deleteByProjectId("proj-1");
        verify(subscriptions).deleteByProjectId("proj-1");
        verify(notifications).deleteByProjectId("proj-1");
        verify(contexts).deleteByEntity("PROJECT", "proj-1");
        verify(workItems).deleteByProjectId("proj-1");
    }
}
