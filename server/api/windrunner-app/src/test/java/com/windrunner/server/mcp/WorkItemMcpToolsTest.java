package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.tools.work.FetchEntriesTool;
import com.windrunner.server.tools.work.FetchRelationshipsTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.AssignedWorkService;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemMcpToolsTest {

    @Mock
    private ProjectSearchService searchService;
    @Mock
    private AssignedWorkService assignedWorkService;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private WorkItemRepository workItems;
    @Mock
    private WorkItemAssigneeRepository assignees;
    @Mock
    private FetchEntriesTool fetchEntries;
    @Mock
    private FetchRelationshipsTool fetchRelationships;
    @Mock
    private HttpServletRequest request;
    private AppUser actor;

    @BeforeEach
    void setUpRequestContext() {
        actor = new AppUser();
        actor.setId("user-1");
        when(request.getAttribute(McpAuthenticationFilter.ACTOR_REQUEST_ATTRIBUTE)).thenReturn(actor);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getWorkItemUsesBoundedPagesForNestedCollections() throws Exception {
        when(externalAccessService.requireScopes(
                request,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ)).thenReturn(actor);

        WorkItem item = new WorkItem();
        item.setId("item-1");
        item.setProjectId("project-1");
        item.setTitle("Investigate alert");
        item.setType("TASK");
        item.setStatus("OPEN");
        when(workItems.findById("item-1")).thenReturn(Optional.of(item));
        when(assignees.findByWorkItemId("item-1")).thenReturn(List.of());

        Entry entry = new Entry();
        entry.setId("entry-1");
        entry.setWorkItemId("item-1");
        when(fetchEntries.execute(new FetchEntriesTool.Parameters("project-1", "item-1", 10, 0), any()))
                .thenReturn(new FetchEntriesTool.Response(List.of(entry), 1, 11, 10, 0, true));
        when(fetchRelationships.execute(new FetchRelationshipsTool.Parameters("project-1", "item-1", 5, 0), any()))
                .thenReturn(new FetchRelationshipsTool.Response(List.of(), 0, 0, 5, 0, false));

        WorkItemMcpTools.WorkItemDetail result = controller().getWorkItem("project-1", "item-1", 10, 5);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entriesTotal()).isEqualTo(11);
        assertThat(result.entriesHasMore()).isTrue();
        assertThat(result.relationshipsHasMore()).isFalse();
        verify(projectAccessService).requireProjectRole(eq("project-1"), eq(actor), eq("VIEWER"));
        verify(externalAccessService).requireScopes(
                request,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ);
        verify(fetchEntries).execute(new FetchEntriesTool.Parameters("project-1", "item-1", 10, 0), any());
        verify(fetchRelationships).execute(new FetchRelationshipsTool.Parameters("project-1", "item-1", 5, 0), any());
    }

    private WorkItemMcpTools controller() {
        return new WorkItemMcpTools(
                searchService,
                assignedWorkService,
                externalAccessService,
                projectAccessService,
                workItems,
                assignees,
                fetchEntries,
                fetchRelationships);
    }
}
