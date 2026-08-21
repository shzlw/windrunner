package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.WorkItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor @RequestMapping("/internal-api/v1/projects/{projectId}/workspace")
public class WorkspaceController {
    private final WorkItemService workItems; private final EntryService entries; private final RelationshipService relationships; private final AuthService auth; private final ProjectAccessService access; private final ProjectSearchService search;
    @GetMapping public ApiResponse<WorkspaceView> get(@PathVariable("projectId") String projectId, @RequestParam(value = "q", required = false) String query, jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        if (query != null && !query.isBlank()) {
            var result = search.search(projectId, query, 100);
            var itemViews = result.workItems().stream().map(item -> new WorkItemView(item, workItems.assignees(item.getId()))).toList();
            return ApiResponse.success(new WorkspaceView(itemViews, result.entries(), result.relationships()));
        }
        var itemViews = workItems.list(projectId).stream().map(item -> new WorkItemView(item, workItems.assignees(item.getId()))).toList();
        return ApiResponse.success(new WorkspaceView(itemViews, entries.list(projectId), relationships.list(projectId)));
    }
}
