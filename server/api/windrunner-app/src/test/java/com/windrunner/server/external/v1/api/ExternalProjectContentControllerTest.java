package com.windrunner.server.external.v1.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.ContentOrderService;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.api.ContentOrderItem;
import com.windrunner.server.work.api.ContentOrderItemRef;
import com.windrunner.server.work.api.ContentReorderRequest;
import com.windrunner.server.work.api.ProjectSearchResult;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExternalProjectContentControllerTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String ACTOR_ID = "user-key-owner";

    @Mock
    private ProjectSearchService searchService;
    @Mock
    private ContentOrderService contentOrderService;
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

    @Test
    void searchBlankQueryReturnsEmptyResultWithoutTouchingSearchService() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());

        ApiResponse<ProjectSearchResult> response = controller().search(PROJECT_ID, "   ", null, request);

        verifyNoInteractions(searchService);
        assertThat(response.data().workItems()).isEmpty();
        assertThat(response.data().entries()).isEmpty();
        assertThat(response.data().relationships()).isEmpty();
    }

    @Test
    void searchDelegatesToServiceAndRequiresViewer() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor());
        ProjectSearchResult result = new ProjectSearchResult(
                List.of(new WorkItem()), List.of(new Entry()), List.of(new Relationship()));
        when(searchService.search(PROJECT_ID, "deploy", 20)).thenReturn(result);

        ApiResponse<ProjectSearchResult> response = controller().search(PROJECT_ID, "deploy", 20, request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.VIEWER);
        assertThat(response.data()).isEqualTo(result);
    }

    @Test
    void reorderRejectsNullBodyAndNullItems() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor());

        assertThatThrownBy(() -> controller().reorder(PROJECT_ID, null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> controller().reorder(PROJECT_ID,
                new ContentReorderRequest(null, null), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(contentOrderService);
    }

    @Test
    void reorderRequiresEditorAndDelegatesToContentOrderService() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor());
        List<ContentOrderItemRef> requestedOrder = List.of(new ContentOrderItemRef("WORK_ITEM", "witm-2"));
        List<ContentOrderItem> reordered = List.of(new ContentOrderItem("WORK_ITEM", "witm-2", 0));
        when(contentOrderService.reorder(eq(PROJECT_ID), eq("parent-1"), eq(requestedOrder))).thenReturn(reordered);

        ApiResponse<List<ContentOrderItem>> response = controller().reorder(
                PROJECT_ID, new ContentReorderRequest("parent-1", requestedOrder), request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(reordered);
    }

    private ExternalProjectContentController controller() {
        return new ExternalProjectContentController(
                searchService, contentOrderService, externalAccessService, projectAccessService);
    }
}
