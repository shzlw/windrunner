package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.WorkItemAiReviewService;
import com.windrunner.server.work.domain.WorkItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/internal-api/v1/projects/{projectId}/work-items")
public class WorkItemController {
    private final WorkItemService service; private final WorkItemAiReviewService aiReviewService; private final AuthService auth; private final ProjectAccessService access;
    @GetMapping public ApiResponse<List<WorkItemView>> list(@PathVariable("projectId") String projectId, jakarta.servlet.http.HttpServletRequest request) { access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER); return ApiResponse.success(service.list(projectId).stream().map(x -> new WorkItemView(x, service.assignees(x.getId()))).toList()); }
    @GetMapping("/{id}") public ApiResponse<WorkItemView> get(@PathVariable("projectId") String projectId, @PathVariable("id") String id, jakarta.servlet.http.HttpServletRequest request) { access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER); WorkItem item = service.get(projectId, id); return ApiResponse.success(new WorkItemView(item, service.assignees(id))); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<WorkItemView> create(@PathVariable("projectId") String projectId, @RequestBody WorkItemRequest body, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); WorkItem item = service.create(projectId, body.workItem(), body.assignees(), actor.getId()); return ApiResponse.success(new WorkItemView(item, service.assignees(item.getId()))); }
    @PutMapping("/{id}") public ApiResponse<WorkItemView> update(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody WorkItemRequest body, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); WorkItem item = service.update(projectId, id, body.workItem(), body.assignees(), actor.getId()); return ApiResponse.success(new WorkItemView(item, service.assignees(id))); }
    @PutMapping("/{id}/move") public ApiResponse<WorkItemView> move(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody WorkItemMoveRequest body, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); WorkItem item = service.move(projectId, id, body, actor.getId()); return ApiResponse.success(new WorkItemView(item, service.assignees(id))); }
    @PostMapping("/{id}/ai-review") public ApiResponse<WorkItemAiReviewResponse> reviewWithAi(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody WorkItemAiReviewRequest body, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(aiReviewService.review(projectId, id, body, actor.getId())); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable("projectId") String projectId, @PathVariable("id") String id, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); service.delete(projectId, id, actor.getId()); return ApiResponse.success(); }
}
