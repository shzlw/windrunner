package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.work.ProjectSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects/{projectId}/search")
public class ProjectSearchController {
    private final ProjectSearchService searchService;
    private final AuthService auth;
    private final ProjectAccessService access;

    @GetMapping
    public ApiResponse<ProjectSearchResult> search(@PathVariable("projectId") String projectId,
                                                   @RequestParam(value = "q", defaultValue = "") String query,
                                                   @RequestParam(value = "limit", required = false) Integer limit,
                                                   jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        return ApiResponse.success(searchService.search(projectId, query, limit));
    }
}
