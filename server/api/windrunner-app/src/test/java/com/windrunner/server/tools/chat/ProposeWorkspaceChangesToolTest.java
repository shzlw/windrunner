package com.windrunner.server.tools.chat;

import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProposeWorkspaceChangesToolTest {

    @Test
    void forwardsTheDraftAndMessageContextToTheProposalService() throws Exception {
        AtomicReference<String> projectId = new AtomicReference<>();
        AtomicReference<String> chatSessionId = new AtomicReference<>();
        AtomicReference<String> sourceMessageId = new AtomicReference<>();
        AtomicReference<String> sourceText = new AtomicReference<>();
        AtomicReference<WorkspaceChangeProposalService.ProposalDraft> receivedDraft = new AtomicReference<>();
        WorkspaceChangeProposalService proposals = new WorkspaceChangeProposalService(null, null, null, null, null, null) {
            @Override
            public WorkspaceChangeProposalView create(String project, String session, String message, String text,
                                                       WorkspaceChangeProposalService.ProposalDraft draft) {
                projectId.set(project);
                chatSessionId.set(session);
                sourceMessageId.set(message);
                sourceText.set(text);
                receivedDraft.set(draft);
                return null;
            }
        };
        AppUser actor = new AppUser();
        actor.setId("user-1");
        var tool = new ProposeWorkspaceChangesTool(proposals).forMessage(
                new ToolExecutionContext(actor, "session-1", List.of("project-1")),
                "project-1", "session-1", "message-1", "Create a task");
        var draft = new WorkspaceChangeProposalService.ProposalDraft(List.of(
                new WorkspaceChangeProposalService.ChangeDraft(
                        "WORK_ITEM", "ADD", null, "new-task", "Create task", null, null, null)));

        assertThat(tool.handler().execute(draft)).isNull();
        assertThat(projectId).hasValue("project-1");
        assertThat(chatSessionId).hasValue("session-1");
        assertThat(sourceMessageId).hasValue("message-1");
        assertThat(sourceText).hasValue("Create a task");
        assertThat(receivedDraft).hasValue(draft);
        assertThat(tool.name()).isEqualTo("propose_workspace_changes");
    }
}
