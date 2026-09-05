package com.windrunner.server.agent.api;

import com.windrunner.server.agent.AgentMessageService;
import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/agent/messages")
public class InternalAgentMessageController {
    private final AuthService authService;
    private final AgentMessageService agentMessageService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AgentMessageResponse> send(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AgentMessageRequest agentMessageRequest,
            HttpServletRequest httpRequest) {
        AppUser actor = authService.requireCurrentUser(httpRequest);
        return ApiResponse.success(agentMessageService.process(actor, idempotencyKey,
                agentMessageRequest == null ? null : agentMessageRequest.message()));
    }
    @GetMapping("/{requestId}")
    public ApiResponse<AgentMessageResponse> get(@PathVariable String requestId, HttpServletRequest httpRequest) {
        return ApiResponse.success(agentMessageService.get(requestId, authService.requireCurrentUser(httpRequest)));
    }
}

