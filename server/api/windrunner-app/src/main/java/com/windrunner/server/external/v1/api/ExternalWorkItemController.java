package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.api.WorkItemMoveRequest;
import com.windrunner.server.work.api.WorkItemRequest;
import com.windrunner.server.work.api.WorkItemView;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExternalWorkItemController {

    private final WorkItemService workItems;
    private final WorkItemRepository workItemRepository;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/projects/{projectId}/work-items")
    public ApiResponse<List<WorkItemView>> list(@PathVariable("projectId") String projectId,
                                                HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        return ApiResponse.success(workItems.list(projectId).stream()
                .map(item -> new WorkItemView(item, workItems.assignees(item.getId())))
                .toList());
    }

    @PostMapping("/projects/{projectId}/work-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkItemView> create(@PathVariable("projectId") String projectId,
                                            @RequestBody WorkItemRequest body,
                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (body == null || body.workItem() == null) {
            throw badRequest("Work item is required");
        }
        WorkItem created = workItems.create(projectId, body.workItem(), body.assignees(), actor.getId());
        return ApiResponse.success(new WorkItemView(created, workItems.assignees(created.getId())));
    }

    @GetMapping("/work-items/{id}")
    public ApiResponse<WorkItemView> get(@PathVariable("id") String id,
                                         HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ);
        WorkItem item = requireWorkItem(id);
        projectAccessService.requireProjectRole(item.getProjectId(), actor, ProjectRoles.VIEWER);
        return ApiResponse.success(new WorkItemView(item, workItems.assignees(item.getId())));
    }

    @PutMapping("/work-items/{id}")
    public ApiResponse<WorkItemView> update(@PathVariable("id") String id,
                                            @RequestBody WorkItemRequest body,
                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        WorkItem current = requireWorkItem(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        if (body == null || body.workItem() == null) {
            throw badRequest("Work item is required");
        }
        WorkItem updated = workItems.update(current.getProjectId(), id, body.workItem(), body.assignees(), actor.getId());
        return ApiResponse.success(new WorkItemView(updated, workItems.assignees(updated.getId())));
    }

    @PutMapping("/work-items/{id}/move")
    public ApiResponse<WorkItemView> move(@PathVariable("id") String id,
                                          @RequestBody WorkItemMoveRequest body,
                                          HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        WorkItem current = requireWorkItem(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        WorkItem moved = workItems.move(current.getProjectId(), id, body, actor.getId());
        return ApiResponse.success(new WorkItemView(moved, workItems.assignees(moved.getId())));
    }

    @DeleteMapping("/work-items/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id,
                                    HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        WorkItem current = requireWorkItem(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        workItems.delete(current.getProjectId(), id, actor.getId());
        return ApiResponse.success();
    }

    private WorkItem requireWorkItem(String id) {
        return workItemRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Work item not found"));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
