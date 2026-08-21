package com.windrunner.server.audit.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/projects/{projectId}/audit-logs")
public class ProjectAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;

    @GetMapping
    public ApiResponse<List<AuditLog>> listProjectAuditLogs(@PathVariable("projectId") String projectId,
                                                            @RequestParam(name = "page", defaultValue = "0") int page,
                                                            @RequestParam(name = "size", defaultValue = "20") int size,
                                                            HttpServletRequest request) {
        projectAccessService.requireProjectRole(projectId, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = auditLogRepository.countLogsByProjectId(projectId);
        List<AuditLog> items = auditLogRepository.findPageByProjectId(
                projectId,
                normalizedSize,
                (long) normalizedPage * normalizedSize);
        return ApiResponse.page(
                items,
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }
}
