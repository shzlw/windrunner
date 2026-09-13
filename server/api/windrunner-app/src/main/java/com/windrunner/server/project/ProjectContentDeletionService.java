package com.windrunner.server.project;

import com.windrunner.server.calendar.persistence.CalendarEventRepository;
import com.windrunner.server.chat.persistence.ChatSessionContextRepository;
import com.windrunner.server.notification.persistence.UserNotificationRepository;
import com.windrunner.server.proposal.ProposalRepository;
import com.windrunner.server.subscription.persistence.SubscriptionRepository;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
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

    private final ProposalRepository proposalRepository;
    private final WorkItemAssigneeRepository workItemAssigneeRepository;
    private final RelationshipRepository relationshipRepository;
    private final EntryRepository entryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final ChatSessionContextRepository chatSessionContextRepository;
    private final WorkItemRepository workItemRepository;
    private final CalendarEventRepository calendarEventRepository;

    @Transactional
    public void deleteProjectContent(String projectId) {
        proposalRepository.deleteChangesByProjectId(projectId);
        proposalRepository.deleteByProjectId(projectId);
        workItemAssigneeRepository.deleteByProjectId(projectId);
        relationshipRepository.deleteByProjectId(projectId);
        entryRepository.deleteByProjectId(projectId);
        subscriptionRepository.deleteByProjectId(projectId);
        userNotificationRepository.deleteByProjectId(projectId);
        chatSessionContextRepository.deleteByEntity("PROJECT", projectId);
        calendarEventRepository.deleteByProjectId(projectId);
        workItemRepository.deleteByProjectId(projectId);
    }
}
