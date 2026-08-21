package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Audit log", description = "Read audit log entries.")
public class ExternalAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final ProjectAccessService projectAccessService;
    private final ExternalAccessService externalAccessService;

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLog>> listAuditLogs(@RequestParam(name = "page", defaultValue = "0") int page,
                                                     @RequestParam(name = "size", defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        externalAccessService.requireAdminScope(request, ApiKeyScopes.AUDIT_LOGS_READ);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = auditLogRepository.countLogs();
        List<AuditLog> items = auditLogRepository.findPage(normalizedSize, (long) normalizedPage * normalizedSize);
        return ApiResponse.page(
                items,
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    @GetMapping("/projects/{projectId}/audit-logs")
    public ApiResponse<List<AuditLog>> listProjectAuditLogs(@PathVariable("projectId") String projectId,
                                                            @RequestParam(name = "page", defaultValue = "0") int page,
                                                            @RequestParam(name = "size", defaultValue = "20") int size,
                                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.AUDIT_LOGS_READ);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
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
