package com.windrunner.server.tools.chat;

import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProposeWorkspaceChangesTool {
    private final WorkspaceChangeProposalService workspaceChangeProposalService;

    public LlmTool<WorkspaceChangeProposalService.ProposalDraft> forMessage(
            ToolExecutionContext context,
            String projectId, String chatSessionId, String sourceMessageId, String sourceText) {
        Objects.requireNonNull(context, "Tool execution context is required");
        String authorizedProjectId = context.requireProjectId(projectId);
        if (context.chatSessionId() != null && !context.chatSessionId().equals(chatSessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The proposal session does not match the tool context");
        }
        return new LlmTool<>(
                "propose_workspace_changes",
                FileUtils.loadSystemPrompt("propose-workspace-changes-tool.md"),
                WorkspaceChangeProposalService.ProposalDraft.class,
                draft -> workspaceChangeProposalService.create(authorizedProjectId, chatSessionId, sourceMessageId, sourceText, draft));
    }
}
