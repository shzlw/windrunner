package com.windrunner.server.audit.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<AuditLog>> listAuditLogs(@RequestParam(name = "page", defaultValue = "0") int page,
                                                     @RequestParam(name = "size", defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        authService.requireAdmin(request);
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
}
