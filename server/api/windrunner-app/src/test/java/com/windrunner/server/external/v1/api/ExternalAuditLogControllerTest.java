package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.audit.AuditLogEnrichmentService;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.external.auth.ExternalAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalAuditLogControllerTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditLogEnrichmentService enrichmentService;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private HttpServletRequest request;

    @Test
    void globalAuditLogsRequireAdminScope() {
        AuditLog log = new AuditLog();
        when(auditLogRepository.countLogs()).thenReturn(1L);
        when(auditLogRepository.findPage(20, 0L)).thenReturn(List.of(log));

        ApiResponse<List<AuditLog>> response = controller().listAuditLogs(0, 20, request);

        verify(externalAccessService).requireAdminScope(request, ApiKeyScopes.AUDIT_LOGS_READ);
        assertThat(response.data()).containsExactly(log);
        assertThat(response.meta().totalItems()).isEqualTo(1L);
    }

    @Test
    void projectAuditLogsAlsoRequireAdminScope() {
        when(auditLogRepository.countLogsByProjectId("proj-1")).thenReturn(0L);
        when(auditLogRepository.findPageByProjectId("proj-1", 20, 0L)).thenReturn(List.of());

        controller().listProjectAuditLogs("proj-1", 0, 20, request);

        verify(externalAccessService).requireAdminScope(request, ApiKeyScopes.AUDIT_LOGS_READ);
    }

    private ExternalAuditLogController controller() {
        return new ExternalAuditLogController(auditLogRepository, enrichmentService, externalAccessService);
    }
}
