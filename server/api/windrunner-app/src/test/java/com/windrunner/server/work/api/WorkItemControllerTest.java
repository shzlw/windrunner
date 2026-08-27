package com.windrunner.server.work.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkItemAiReviewService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.WorkItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkItemControllerTest {

    private static final String PROJECT_ID = "project-1";

    @Mock
    private WorkItemService service;
    @Mock
    private WorkItemAiReviewService aiReviewService;
    @Mock
    private AuthService auth;
    @Mock
    private ProjectAccessService access;
    @Mock
    private HttpServletRequest request;

    @Test
    void listTreeReturnsPagedChildren() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        WorkItem item = new WorkItem();
        item.setId("item-1");
        item.setProjectId(PROJECT_ID);
        item.setParentWorkItemId("parent-1");

        when(auth.requireCurrentUser(request)).thenReturn(actor);
        when(service.listPage(PROJECT_ID, "parent-1", 1, 25)).thenReturn(List.of(item));
        when(service.views(List.of(item))).thenReturn(List.of(new WorkItemView(item, List.of())));
        when(service.countByParent(PROJECT_ID, "parent-1")).thenReturn(42L);

        var response = controller().listTree(PROJECT_ID, "parent-1", 1, 25, request);

        verify(access).requireProjectRole(PROJECT_ID, actor, ProjectRoles.VIEWER);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).workItem()).isSameAs(item);
        assertThat(response.meta().page()).isEqualTo(1);
        assertThat(response.meta().size()).isEqualTo(25);
        assertThat(response.meta().totalItems()).isEqualTo(42L);
        assertThat(response.meta().totalPages()).isEqualTo(2);
    }

    @Test
    void listTreeSubtreeReturnsOneBoundedResult() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        WorkItem item = new WorkItem();
        item.setId("item-1");
        item.setProjectId(PROJECT_ID);
        item.setParentWorkItemId("root-1");

        when(auth.requireCurrentUser(request)).thenReturn(actor);
        when(service.listSubtree(PROJECT_ID, "root-1", 20, 1001)).thenReturn(List.of(item));
        when(service.views(List.of(item))).thenReturn(List.of(new WorkItemView(item, List.of())));

        var response = controller().listTreeSubtree(PROJECT_ID, "root-1", 20, 1000, request);

        verify(access).requireProjectRole(PROJECT_ID, actor, ProjectRoles.VIEWER);
        assertThat(response.data().items()).hasSize(1);
        assertThat(response.data().items().get(0).workItem()).isSameAs(item);
        assertThat(response.data().truncated()).isFalse();
    }

    private WorkItemController controller() {
        return new WorkItemController(service, aiReviewService, auth, access);
    }
}
