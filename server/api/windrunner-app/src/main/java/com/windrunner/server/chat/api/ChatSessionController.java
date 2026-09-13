package com.windrunner.server.chat.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.domain.UserContext;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/chat-sessions")
public class ChatSessionController {
    private static final String PROJECT_FOCUS_PATH = "project-focus";

    private final ChatService chatService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<ChatSessionPageView> list(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request) {
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.listSessions(user.userId(), search != null ? search : q, limit, offset));
    }

    @PostMapping
    public ApiResponse<ChatSessionView> create(HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.createSession(user.userId(), actor));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSessionView> get(@PathVariable String sessionId, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.getSession(sessionId, user.userId(), actor));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<ChatMessagePageView> messages(@PathVariable String sessionId,
                                                     @RequestParam(defaultValue = "30") int limit,
                                                     @RequestParam(required = false) String before,
                                                     HttpServletRequest request) {
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.getMessagePage(sessionId, user.userId(), limit, before));
    }

    @PatchMapping("/{sessionId}/title")
    public ApiResponse<Void> rename(@PathVariable String sessionId,
                                    @RequestBody RenameChatSessionRequest body,
                                    HttpServletRequest request) {
        UserContext user = authService.requireUserContext(request);
        chatService.renameSession(sessionId, user.userId(), body == null ? null : body.title());
        return ApiResponse.success();
    }

    @GetMapping("/{sessionId}/context")
    public ApiResponse<List<ChatSessionContextView>> context(@PathVariable String sessionId, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.listContexts(sessionId, user.userId(), actor));
    }

    @PostMapping("/{sessionId}/context")
    public ApiResponse<ChatSessionContextView> addContext(@PathVariable String sessionId,
                                                          @RequestBody ChatSessionContextRequest body,
                                                          HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.addContext(sessionId, user.userId(), actor,
                body == null ? null : body.entityType(), body == null ? null : body.entityId()));
    }

    @PutMapping("/{sessionId}/context/" + PROJECT_FOCUS_PATH)
    public ApiResponse<ChatSessionContextView> setProjectFocus(@PathVariable String sessionId,
                                                               @RequestBody ChatProjectFocusRequest body,
                                                               HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        UserContext user = authService.requireUserContext(request);
        return ApiResponse.success(chatService.setProjectFocus(sessionId, user.userId(), actor,
                body == null ? null : body.projectId()));
    }

    @DeleteMapping("/{sessionId}/context/" + PROJECT_FOCUS_PATH)
    public ApiResponse<Void> clearProjectFocus(@PathVariable String sessionId, HttpServletRequest request) {
        UserContext user = authService.requireUserContext(request);
        chatService.clearProjectFocus(sessionId, user.userId());
        return ApiResponse.success();
    }

    @DeleteMapping("/{sessionId}/context/{contextId}")
    public ApiResponse<Void> deleteContext(@PathVariable String sessionId,
                                           @PathVariable String contextId,
                                           HttpServletRequest request) {
        UserContext user = authService.requireUserContext(request);
        chatService.deleteContext(sessionId, contextId, user.userId());
        return ApiResponse.success();
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(@PathVariable String sessionId, HttpServletRequest request) {
        UserContext user = authService.requireUserContext(request);
        chatService.deleteSession(sessionId, user.userId());
        return ApiResponse.success();
    }

    public record RenameChatSessionRequest(String title) {
    }
}
