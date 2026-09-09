package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects/{projectId}/graph-change-proposals")
public class WorkspaceChangeProposalController {
    private final WorkspaceChangeProposalService workspaceChangeProposalService;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;
    private final ChatService chatService;

    @GetMapping
    public ApiResponse<List<WorkspaceChangeProposalView>> list(@PathVariable("projectId") String projectId,
                                                               @RequestParam(name = "chatSessionId", required = false) String chatSessionId,
                                                               jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        if (chatSessionId != null && !chatSessionId.isBlank()) {
            chatService.getSessionForChat(chatSessionId.trim(), actor.getId());
        }
        return ApiResponse.success(workspaceChangeProposalService.list(projectId, chatSessionId));
    }

    @PostMapping("/{proposalId}/changes/{changeId}/decision")
    public ApiResponse<WorkspaceChangeProposalView> decide(@PathVariable("projectId") String projectId,
                                                           @PathVariable("proposalId") String proposalId,
                                                           @PathVariable("changeId") String changeId,
                                                           @RequestBody DecisionRequest body,
                                                           jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return ApiResponse.success(workspaceChangeProposalService.decide(projectId, proposalId, changeId, body, actor.getId()));
    }

    @PostMapping("/{proposalId}/decision")
    public ApiResponse<WorkspaceChangeProposalView> decideAll(@PathVariable("projectId") String projectId,
                                                              @PathVariable("proposalId") String proposalId,
                                                              @RequestBody DecisionRequest body,
                                                              jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return ApiResponse.success(workspaceChangeProposalService.decideAll(projectId, proposalId, body, actor.getId()));
    }
}
