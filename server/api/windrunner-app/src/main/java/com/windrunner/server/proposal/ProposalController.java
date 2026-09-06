package com.windrunner.server.proposal;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/chat-sessions/{sessionId}/identity-proposals")
public class ProposalController {
    private final ProposalService proposalService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<ProposalPage> list(@PathVariable String sessionId,
                                                          @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
                                                          HttpServletRequest request) {
        return ApiResponse.success(proposalService.list(sessionId, authService.requireCurrentUser(request), limit, offset));
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<ProposalView> decide(@PathVariable String sessionId, @PathVariable String id,
                                                            @RequestBody Decision decision, HttpServletRequest request) {
        return ApiResponse.success(proposalService.decide(sessionId, id, decision == null ? null : decision.decision(), authService.requireCurrentUser(request)));
    }

    public record Decision(String decision) {
    }
}
