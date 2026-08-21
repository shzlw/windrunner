package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.work.ContentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects/{projectId}/content-order")
public class ContentOrderController {
    private final ContentOrderService service;
    private final AuthService auth;
    private final ProjectAccessService access;

    @PutMapping
    public ApiResponse<List<ContentOrderItem>> reorder(
            @PathVariable("projectId") String projectId,
            @RequestBody ContentReorderRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(servletRequest), ProjectRoles.EDITOR);
        return ApiResponse.success(service.reorder(projectId, request.parentWorkItemId(), request.items()));
    }
}
