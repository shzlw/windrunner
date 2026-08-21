package com.windrunner.server.subscription.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.subscription.SubscriptionService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AuthService authService;

    @GetMapping("/projects/{projectId}/work-items/{workItemId}/subscription")
    public ApiResponse<SubscriptionStatus> getStatus(@PathVariable("projectId") String projectId,
                                                     @PathVariable("workItemId") String workItemId,
                                                     HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(new SubscriptionStatus(subscriptionService.isSubscribed(actor, projectId, workItemId)));
    }

    @PostMapping("/projects/{projectId}/work-items/{workItemId}/subscription")
    public ApiResponse<SubscriptionStatus> subscribe(@PathVariable("projectId") String projectId,
                                                     @PathVariable("workItemId") String workItemId,
                                                     HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(new SubscriptionStatus(subscriptionService.subscribe(actor, projectId, workItemId)));
    }

    @DeleteMapping("/projects/{projectId}/work-items/{workItemId}/subscription")
    public ApiResponse<SubscriptionStatus> unsubscribe(@PathVariable("projectId") String projectId,
                                                       @PathVariable("workItemId") String workItemId,
                                                       HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(new SubscriptionStatus(subscriptionService.unsubscribe(actor, projectId, workItemId)));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<SubscriptionView>> list(@RequestParam(name = "page", defaultValue = "0") int page,
                                                    @RequestParam(name = "size", defaultValue = "50") int size,
                                                    HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        SubscriptionPageResponse response = subscriptionService.listForUser(actor, page, size);
        return ApiResponse.page(
                response.items(),
                response.page(),
                response.size(),
                response.totalItems(),
                response.totalPages());
    }
}