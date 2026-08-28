package com.windrunner.server.chat.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.domain.UserContext;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/projects/{projectId}/chat-sessions")
public class ChatSessionController {

    private final ChatService chatService;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;

    @GetMapping
    public ApiResponse<ChatSessionView> getActive(
            @PathVariable("projectId") String projectId,
            HttpServletRequest request
    ) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.getActiveSession(projectId, user.userId()));
    }

    @GetMapping("/history")
    public ApiResponse<List<ChatSessionSummaryView>> list(
            @PathVariable("projectId") String projectId,
            HttpServletRequest request
    ) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.listSessions(projectId, user.userId()));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSessionView> getById(
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId,
            HttpServletRequest request
    ) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.getSession(projectId, sessionId, user.userId()));
    }

    @PostMapping("/new")
    public ApiResponse<ChatSessionView> startNew(
            @PathVariable("projectId") String projectId,
            HttpServletRequest request
    ) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.startNewSession(projectId, user.userId()));
    }
}
