package com.windrunner.server.audit.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audit.AuditLogEnrichmentService;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.work.WorkItemService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/projects/{projectId}/work-items/{workItemId}/audit-logs")
public class WorkItemAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;
    private final WorkItemService workItemService;
    private final AuditLogEnrichmentService auditLogEnrichmentService;

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
        auditLogEnrichmentService.enrich(items);
        return ApiResponse.page(
                items,
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

}
