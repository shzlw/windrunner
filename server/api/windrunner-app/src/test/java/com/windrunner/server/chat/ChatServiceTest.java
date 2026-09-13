package com.windrunner.server.chat;

import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.domain.ChatSessionContext;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionContextRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.chat.persistence.ChatSessionSummaryRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.proposal.ProposalRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatSessionContextRepository contextRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatSessionSummaryRepository summaryRepository;
    @Mock
    private WorkspaceChangeProposalRepository workspaceChangeProposalRepository;
    @Mock
    private WorkspaceChangeRepository workspaceChangeRepository;
    @Mock
    private EntityIdGenerator idGenerator;
    @Mock
    private ProposalRepository proposalRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private WorkItemRepository workItemRepository;

    @InjectMocks
    private ChatService chatService;

    @Test
    void replacesTheFocusedProjectAndRemovesWorkItemsFromThePreviousProject() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        ChatSession session = new ChatSession();
        session.setId("session-1");
        session.setUserId("user-1");
        Project project = new Project();
        project.setId("project-2");
        project.setName("Payments");
        WorkItem oldWorkItem = new WorkItem();
        oldWorkItem.setId("work-item-1");
        oldWorkItem.setProjectId("project-1");

        ChatSessionContext oldProject = context("context-1", ChatContextEntityTypes.PROJECT, "project-1");
        ChatSessionContext oldWorkItemContext = context("context-2", ChatContextEntityTypes.WORK_ITEM, "work-item-1");
        ChatSessionContext teamContext = context("context-3", ChatContextEntityTypes.TEAM, "team-1");
        ChatSessionContext newProjectContext = context("context-4", ChatContextEntityTypes.PROJECT, "project-2");

        when(sessionRepository.findByIdAndUserId("session-1", "user-1")).thenReturn(Optional.of(session));
        when(projectRepository.findById("project-2")).thenReturn(Optional.of(project));
        when(contextRepository.findBySessionId("session-1")).thenReturn(List.of(oldProject, oldWorkItemContext, teamContext));
        when(workItemRepository.findById("work-item-1")).thenReturn(Optional.of(oldWorkItem));
        when(idGenerator.generate(EntityIdType.CHAT_SESSION_CONTEXT)).thenReturn("context-4");
        when(contextRepository.findByIdAndSessionId("context-4", "session-1")).thenReturn(Optional.of(newProjectContext));

        var result = chatService.setProjectFocus("session-1", "user-1", actor, "project-2");

        assertThat(result.entityId()).isEqualTo("project-2");
        assertThat(result.label()).isEqualTo("Payments");
        verify(projectAccessService).requireProjectRole("project-2", actor, ProjectRoles.VIEWER);
        verify(contextRepository).delete("context-1", "session-1");
        verify(contextRepository).delete("context-2", "session-1");
        verify(contextRepository, never()).delete("context-3", "session-1");
        verify(contextRepository).insert("context-4", "session-1", ChatContextEntityTypes.PROJECT, "project-2");
    }

    private ChatSessionContext context(String id, String entityType, String entityId) {
        ChatSessionContext context = new ChatSessionContext();
        context.setId(id);
        context.setChatSessionId("session-1");
        context.setEntityType(entityType);
        context.setEntityId(entityId);
        return context;
    }
}
