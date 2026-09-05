package com.windrunner.server.identity;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/chat-sessions/{sessionId}/identity-proposals")
public class IdentityProposalController {
    private final IdentityProposalService proposals;
    private final AuthService auth;

    @GetMapping
    public ApiResponse<IdentityProposalService.Page> list(@PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request) {
        return ApiResponse.success(proposals.list(sessionId, auth.requireCurrentUser(request), limit, offset));
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<IdentityProposalService.View> decide(@PathVariable String sessionId, @PathVariable String id,
            @RequestBody Decision decision, HttpServletRequest request) {
        return ApiResponse.success(proposals.decide(sessionId, id, decision == null ? null : decision.decision(), auth.requireCurrentUser(request)));
    }
    public record Decision(String decision) { }
}
