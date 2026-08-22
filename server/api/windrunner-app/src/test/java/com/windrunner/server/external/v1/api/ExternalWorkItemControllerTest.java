package com.windrunner.server.external.v1.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.WorkItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExternalWorkItemControllerTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String ACTOR_ID = "user-key-owner";

    @Mock
    private WorkItemService workItems;
    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private HttpServletRequest request;

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId(ACTOR_ID);
        return actor;
    }

    private WorkItem workItem(String id, String projectId, String status) {
        WorkItem item = new WorkItem();
        item.setId(id);
        item.setProjectId(projectId);
        item.setStatus(status);
        return item;
    }

    @Test
    void listRequiresReadScopeAndProjectViewer() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());

        controller().list(PROJECT_ID, 0, 50, null, null, null, null, request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.VIEWER);
    }

    @Test
    void listReturnsPagedEnvelopeWithAssignees() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());
        WorkItem item = workItem("witm-1", PROJECT_ID, "OPEN");
        List<WorkItemAssignee> assignees = List.of(new WorkItemAssignee());
        when(workItemRepository.findPageForProject(PROJECT_ID, null, null, null, null, 25, 0L)).thenReturn(List.of(item));
        when(workItemRepository.countForProject(PROJECT_ID, null, null, null, null)).thenReturn(42L);
        when(workItems.assignees("witm-1")).thenReturn(assignees);

        ApiResponse<List<ExternalWorkItemResponse>> response = controller().list(
                PROJECT_ID, 0, 25, null, null, null, null, request);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0)).isEqualTo(ExternalWorkItemResponse.from(item, assignees));
        assertThat(response.meta().page()).isZero();
        assertThat(response.meta().size()).isEqualTo(25);
        assertThat(response.meta().totalItems()).isEqualTo(42L);
        assertThat(response.meta().totalPages()).isEqualTo(2);
    }

    @Test
    void listNormalizesPaginationAndBlankFilters() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());
        when(workItemRepository.findPageForProject(PROJECT_ID, "OPEN", null, null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), 100, 0L)).thenReturn(List.of());
        when(workItemRepository.countForProject(PROJECT_ID, "OPEN", null, null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"))).thenReturn(0L);

        // lowercase input is uppercased to match how enum values are stored
        controller().list(PROJECT_ID, -3, 500, " open ", " ", "", OffsetDateTime.parse("2026-01-01T00:00:00Z"), request);

        verify(workItemRepository).findPageForProject(PROJECT_ID, "OPEN", null, null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), 100, 0L);
    }

    @Test
    void createRequiresEditorRoleAndDelegatesToServiceWithActor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor());
        WorkItem requested = workItem(null, PROJECT_ID, "OPEN");
        WorkItem saved = workItem("witm-new", PROJECT_ID, "OPEN");
        List<WorkItemAssignee> assignees = List.of(new WorkItemAssignee());
        when(workItems.create(PROJECT_ID, requested, assignees, ACTOR_ID)).thenReturn(saved);
        when(workItems.assignees("witm-new")).thenReturn(assignees);

        ApiResponse<ExternalWorkItemResponse> response = controller().create(
                PROJECT_ID, new WorkItemRequest(requested, assignees), request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(ExternalWorkItemResponse.from(saved, assignees));
    }

    @Test
    void createRejectsNullBody() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor());

        assertThatThrownBy(() -> controller().create(PROJECT_ID, null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> controller().create(PROJECT_ID, new WorkItemRequest(null, null), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(workItems, never()).create(any(), any(), any(), any());
    }

    @Test
    void getResolvesProjectFromItemAndEnforcesViewerOnThatProject() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());
        WorkItem item = workItem("witm-1", "project-other", "DONE");
        when(workItemRepository.findById("witm-1")).thenReturn(Optional.of(item));
        when(workItems.assignees("witm-1")).thenReturn(List.of());

        ApiResponse<ExternalWorkItemResponse> response = controller().get("witm-1", request);

        verify(projectAccessService).requireProjectRole("project-other", actor(), ProjectRoles.VIEWER);
        assertThat(response.data()).isEqualTo(ExternalWorkItemResponse.from(item, List.of()));
    }

    @Test
    void getUnknownWorkItemReturns404WithoutAuthorizationCheck() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());
        when(workItemRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().get("missing", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(projectAccessService);
    }

    @Test
    void deleteResolvesProjectFromItemAndRequiresEditor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor());
        WorkItem item = workItem("witm-1", "project-other", "DONE");
        when(workItemRepository.findById("witm-1")).thenReturn(Optional.of(item));

        controller().delete("witm-1", request);

        verify(projectAccessService).requireProjectRole("project-other", actor(), ProjectRoles.EDITOR);
        verify(workItems).delete("project-other", "witm-1", ACTOR_ID);
    }

    @Test
    void moveResolvesProjectFromItemAndDelegates() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor());
        WorkItem current = workItem("witm-1", PROJECT_ID, "OPEN");
        WorkItem moved = workItem("witm-1", PROJECT_ID, "OPEN");
        when(workItemRepository.findById("witm-1")).thenReturn(Optional.of(current));
        when(workItems.move(eq(PROJECT_ID), eq("witm-1"), any(WorkItemMoveRequest.class), eq(ACTOR_ID))).thenReturn(moved);

        ApiResponse<ExternalWorkItemResponse> response = controller().move(
                "witm-1", new WorkItemMoveRequest("parent-1", null, null), request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(ExternalWorkItemResponse.from(moved, List.of()));
    }

    private ExternalWorkItemController controller() {
        return new ExternalWorkItemController(workItems, workItemRepository, externalAccessService, projectAccessService);
    }
}
