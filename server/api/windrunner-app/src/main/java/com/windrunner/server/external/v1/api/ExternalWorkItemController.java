package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalWorkItemResponse;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.api.WorkItemMoveRequest;
import com.windrunner.server.work.api.WorkItemRequest;
import com.windrunner.server.work.api.WorkItemView;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Work items", description = "Create and manage work items.")
public class ExternalWorkItemController {

    private final WorkItemService workItems;
    private final WorkItemRepository workItemRepository;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/projects/{projectId}/work-items")
    public ApiResponse<List<ExternalWorkItemResponse>> list(@PathVariable("projectId") String projectId,
                                                @RequestParam(name = "page", defaultValue = "0") int page,
                                                @RequestParam(name = "size", defaultValue = "50") int size,
                                                @RequestParam(name = "status", required = false) String status,
                                                @RequestParam(name = "type", required = false) String type,
                                                @RequestParam(name = "priority", required = false) String priority,
                                                @RequestParam(name = "updated_after", required = false)
                                                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                                java.time.OffsetDateTime updatedAfter,
                                                HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<WorkItem> items = workItemsPage(projectId, normalizedSize, (long) normalizedPage * normalizedSize, status, type, priority, updatedAfter);
        long totalItems = workItemsCount(projectId, status, type, priority, updatedAfter);
        return ApiResponse.page(
                items.stream().map(item -> ExternalWorkItemResponse.from(item, workItems.assignees(item.getId()))).toList(),
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    private List<WorkItem> workItemsPage(String projectId, int limit, long offset, String status, String type, String priority, java.time.OffsetDateTime updatedAfter) {
        return workItemRepository.findPageForProject(projectId,
                normalizedEnumFilter(status),
                normalizedEnumFilter(type),
                normalizedEnumFilter(priority),
                updatedAfter,
                limit,
                offset);
    }

    private long workItemsCount(String projectId, String status, String type, String priority, java.time.OffsetDateTime updatedAfter) {
        return workItemRepository.countForProject(projectId,
                normalizedEnumFilter(status),
                normalizedEnumFilter(type),
                normalizedEnumFilter(priority),
                updatedAfter);
    }

    /**
     * Enum filters are stored uppercase (the write path enforces this via
     * enumValue), so matching is case-insensitive for callers. Blank values
     * mean "no filter".
     */
    private static String normalizedEnumFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    @PostMapping("/projects/{projectId}/work-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExternalWorkItemResponse> create(@PathVariable("projectId") String projectId,
                                            @RequestBody WorkItemRequest body,
                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (body == null || body.workItem() == null) {
            throw badRequest("Work item is required");
        }
        WorkItem created = workItems.create(projectId, body.workItem(), body.assignees(), actor.getId());
        return ApiResponse.success(ExternalWorkItemResponse.from(created, workItems.assignees(created.getId())));
    }

    @GetMapping("/work-items/{id}")
    public ApiResponse<ExternalWorkItemResponse> get(@PathVariable("id") String id,
                                         HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ);
        WorkItem item = requireWorkItem(id);
        projectAccessService.requireProjectRole(item.getProjectId(), actor, ProjectRoles.VIEWER);
        return ApiResponse.success(ExternalWorkItemResponse.from(item, workItems.assignees(item.getId())));
    }

    @PutMapping("/work-items/{id}")
    public ApiResponse<ExternalWorkItemResponse> update(@PathVariable("id") String id,
                                            @RequestBody WorkItemRequest body,
                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        WorkItem current = requireWorkItem(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        if (body == null || body.workItem() == null) {
            throw badRequest("Work item is required");
        }
        WorkItem updated = workItems.update(current.getProjectId(), id, body.workItem(), body.assignees(), actor.getId());
        return ApiResponse.success(ExternalWorkItemResponse.from(updated, workItems.assignees(updated.getId())));
    }

    @PutMapping("/work-items/{id}/move")
    public ApiResponse<ExternalWorkItemResponse> move(@PathVariable("id") String id,
                                          @RequestBody WorkItemMoveRequest body,
                                          HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE);
        WorkItem current = requireWorkItem(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        WorkItem moved = workItems.move(current.getProjectId(), id, body, actor.getId());
        return ApiResponse.success(ExternalWorkItemResponse.from(moved, workItems.assignees(moved.getId())));
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
