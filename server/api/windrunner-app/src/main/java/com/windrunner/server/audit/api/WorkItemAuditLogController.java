package com.windrunner.server.audit.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.WorkItemService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/projects/{projectId}/work-items/{workItemId}/audit-logs")
public class WorkItemAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;
    private final WorkItemService workItemService;
    private final AppUserRepository users;

    @GetMapping
    public ApiResponse<List<AuditLog>> listWorkItemAuditLogs(@PathVariable("projectId") String projectId,
                                                             @PathVariable("workItemId") String workItemId,
                                                             @RequestParam(name = "page", defaultValue = "0") int page,
                                                             @RequestParam(name = "size", defaultValue = "20") int size,
                                                             HttpServletRequest request) {
        projectAccessService.requireProjectRole(projectId, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        workItemService.get(projectId, workItemId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = auditLogRepository.countLogsByWorkItemId(workItemId, projectId);
        List<AuditLog> items = auditLogRepository.findPageByWorkItemId(
                workItemId,
                projectId,
                normalizedSize,
                (long) normalizedPage * normalizedSize);
        Map<String, AppUser> actorsById = new LinkedHashMap<>();
        users.findAllById(items.stream()
                        .map(AuditLog::getActorUserId)
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList())
                .forEach(user -> actorsById.put(user.getId(), user));
        items.forEach(item -> item.setActorDisplayName(displayName(actorsById.get(item.getActorUserId()))));
        return ApiResponse.page(
                items,
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    private String displayName(AppUser user) {
        if (user == null) {
            return null;
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return user.getEmail();
    }
}
