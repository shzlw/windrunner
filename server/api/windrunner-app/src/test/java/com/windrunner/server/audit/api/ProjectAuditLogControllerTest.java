package com.windrunner.server.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audit.domain.AuditLog;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAuditLogControllerTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuthService authService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private HttpServletRequest request;

    @Test
    void listProjectAuditLogsRequiresProjectViewerAndReturnsProjectPage() {
        ProjectAuditLogController controller = new ProjectAuditLogController(
                auditLogRepository,
                authService,
                projectAccessService);
        AppUser actor = new AppUser();
        actor.setId("user-1");
        AuditLog auditLog = new AuditLog();
        auditLog.setId("audit-1");
        when(authService.requireCurrentUser(request)).thenReturn(actor);
        when(auditLogRepository.countLogsByProjectId("project-1")).thenReturn(1L);
        when(auditLogRepository.findPageByProjectId("project-1", 20, 0L)).thenReturn(List.of(auditLog));

        ApiResponse<List<AuditLog>> response = controller.listProjectAuditLogs("project-1", 0, 20, request);

        verify(projectAccessService).requireProjectRole("project-1", actor, ProjectRoles.VIEWER);
        assertThat(response.data()).containsExactly(auditLog);
        assertThat(response.meta().page()).isZero();
        assertThat(response.meta().size()).isEqualTo(20);
        assertThat(response.meta().totalItems()).isEqualTo(1L);
        assertThat(response.meta().totalPages()).isEqualTo(1);
    }
}
