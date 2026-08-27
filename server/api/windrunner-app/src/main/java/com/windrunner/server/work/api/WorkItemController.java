package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkItemAiReviewService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects/{projectId}/work-items")
public class WorkItemController {
    private static final int MAX_SUBTREE_DEPTH = 20;
    private static final int MAX_SUBTREE_ITEMS = 1000;

    private final WorkItemService service;
    private final WorkItemAiReviewService aiReviewService;
    private final AuthService auth;
    private final ProjectAccessService access;

    @GetMapping
    public ApiResponse<List<WorkItemView>> list(@PathVariable("projectId") String projectId, jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        return ApiResponse.success(service.views(service.list(projectId)));
    }

    @GetMapping("/tree")
    public ApiResponse<List<WorkItemView>> listTree(
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "parentWorkItemId", required = false) String parentWorkItemId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<WorkItemView> items = service.views(service.listPage(projectId, parentWorkItemId, normalizedPage, normalizedSize));
        long totalItems = service.countByParent(projectId, parentWorkItemId);
        return ApiResponse.page(items, normalizedPage, normalizedSize, totalItems, (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    @GetMapping("/tree/subtree")
    public ApiResponse<WorkItemSubtreeView> listTreeSubtree(
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "rootWorkItemId") String rootWorkItemId,
            @RequestParam(name = "maxDepth", defaultValue = "20") int maxDepth,
            @RequestParam(name = "maxItems", defaultValue = "1000") int maxItems,
            jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        int normalizedDepth = Math.max(1, Math.min(maxDepth, MAX_SUBTREE_DEPTH));
        int normalizedMaxItems = Math.max(1, Math.min(maxItems, MAX_SUBTREE_ITEMS));
        List<WorkItem> descendants = service.listSubtree(projectId, rootWorkItemId, normalizedDepth, normalizedMaxItems + 1);
        boolean truncated = descendants.size() > normalizedMaxItems;
        if (truncated) {
            descendants = descendants.subList(0, normalizedMaxItems);
        }
        return ApiResponse.success(new WorkItemSubtreeView(service.views(descendants), truncated));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkItemView> get(@PathVariable("projectId") String projectId, @PathVariable("id") String id, jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        WorkItem item = service.get(projectId, id);
        return ApiResponse.success(new WorkItemView(item, service.assignees(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkItemView> create(@PathVariable("projectId") String projectId, @RequestBody WorkItemRequest body, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        WorkItem item = service.create(projectId, body.workItem(), body.assignees(), actor.getId());
        return ApiResponse.success(new WorkItemView(item, service.assignees(item.getId())));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkItemView> update(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody WorkItemRequest body, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        WorkItem item = service.update(projectId, id, body.workItem(), body.assignees(), actor.getId());
        return ApiResponse.success(new WorkItemView(item, service.assignees(id)));
    }

    @PutMapping("/{id}/move")
    public ApiResponse<WorkItemView> move(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody WorkItemMoveRequest body, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        WorkItem item = service.move(projectId, id, body, actor.getId());
        return ApiResponse.success(new WorkItemView(item, service.assignees(id)));
    }

    @PostMapping("/{id}/ai-review")
    public ApiResponse<WorkItemAiReviewResponse> reviewWithAi(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody WorkItemAiReviewRequest body, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return ApiResponse.success(aiReviewService.review(projectId, id, body, actor.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("projectId") String projectId, @PathVariable("id") String id, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        service.delete(projectId, id, actor.getId());
        return ApiResponse.success();
    }
}
