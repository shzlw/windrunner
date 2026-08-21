package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.AssignedWorkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/assigned-to-me")
public class AssignedWorkController {

    private final AssignedWorkService assignedWorkService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<AssignedWorkItemView>> list(@RequestParam(name = "page", defaultValue = "0") int page,
                                                        @RequestParam(name = "size", defaultValue = "50") int size,
                                                        HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        List<AssignedWorkItemView> items = assignedWorkService.listAssignedToUser(actor, page, size);
        long totalItems = assignedWorkService.countAssignedToUser(actor);
        return ApiResponse.page(items, Math.max(page, 0), size, totalItems, (int) Math.ceil(totalItems / (double) Math.min(Math.max(size, 1), 50)));
    }
}
