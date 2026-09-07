package com.windrunner.server.project;

import com.windrunner.server.calendar.persistence.CalendarEventRepository;
import com.windrunner.server.chat.persistence.ChatSessionContextRepository;
import com.windrunner.server.notification.persistence.UserNotificationRepository;
import com.windrunner.server.subscription.persistence.SubscriptionRepository;
import com.windrunner.server.work.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectContentDeletionServiceTest {

    @Mock
    private WorkspaceChangeRepository workspaceChangeRepository;
    @Mock
    private WorkspaceChangeProposalRepository workspaceChangeProposalRepository;
    @Mock
    private WorkItemAssigneeRepository workItemAssigneeRepository;
    @Mock
    private RelationshipRepository relationshipRepository;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserNotificationRepository userNotificationRepository;
    @Mock
    private ChatSessionContextRepository chatSessionContextRepository;
    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Test
    void deletesProjectOwnedOperationalDataButNotHistoryTables() {
        new ProjectContentDeletionService(workspaceChangeRepository, workspaceChangeProposalRepository, workItemAssigneeRepository, relationshipRepository, entryRepository,
                subscriptionRepository, userNotificationRepository, chatSessionContextRepository, workItemRepository, calendarEventRepository).deleteProjectContent("proj-1");

        verify(workspaceChangeRepository).deleteByProjectId("proj-1");
        verify(workspaceChangeProposalRepository).deleteByProjectId("proj-1");
        verify(workItemAssigneeRepository).deleteByProjectId("proj-1");
        verify(relationshipRepository).deleteByProjectId("proj-1");
        verify(entryRepository).deleteByProjectId("proj-1");
        verify(subscriptionRepository).deleteByProjectId("proj-1");
        verify(userNotificationRepository).deleteByProjectId("proj-1");
        verify(chatSessionContextRepository).deleteByEntity("PROJECT", "proj-1");
        verify(calendarEventRepository).deleteByProjectId("proj-1");
        verify(workItemRepository).deleteByProjectId("proj-1");
    }
}
