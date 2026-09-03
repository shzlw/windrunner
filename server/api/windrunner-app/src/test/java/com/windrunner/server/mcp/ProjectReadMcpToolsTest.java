package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.tools.work.FetchProjectSummaryTool;
import com.windrunner.server.tools.work.FetchWorkItemsTool;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectReadMcpToolsTest {

    private static final String PROJECT_ID = "proj-1";

    @Mock
    private McpAuthorization authorization;
    @Mock
    private FetchWorkItemsTool workItems;
    @Mock
    private com.windrunner.server.tools.work.FetchEntriesTool entries;
    @Mock
    private com.windrunner.server.tools.work.FetchRelationshipsTool relationships;
    @Mock
    private com.windrunner.server.tools.work.FetchProjectBlockersTool blockers;
    @Mock
    private FetchProjectSummaryTool summary;

    @Test
    void listWorkItemsAuthorizesProjectAndPassesPageArguments() throws Exception {
        when(authorization.requireProjectId(" " + PROJECT_ID + " ")).thenReturn(PROJECT_ID);
        AppUser actor = actor();
        when(authorization.requireProjectViewer(PROJECT_ID, ApiKeyScopes.WORK_ITEMS_READ)).thenReturn(actor);
        when(authorization.toolContext(actor, PROJECT_ID)).thenReturn(context(actor));
        when(workItems.execute(any(FetchWorkItemsTool.Parameters.class), any())).thenReturn(null);

        assertThat(tools().listWorkItems(" " + PROJECT_ID + " ", "deployment", 10, 20)).isNull();

        verify(authorization).requireProjectViewer(PROJECT_ID, ApiKeyScopes.WORK_ITEMS_READ);
        ArgumentCaptor<FetchWorkItemsTool.Parameters> parameters = ArgumentCaptor.forClass(FetchWorkItemsTool.Parameters.class);
        verify(workItems).execute(parameters.capture(), any());
        assertThat(parameters.getValue()).isEqualTo(
                new FetchWorkItemsTool.Parameters(PROJECT_ID, "deployment", 10, 20));
    }

    @Test
    void projectSummaryUsesAggregateToolAndAllReadScopes() throws Exception {
        when(authorization.requireProjectId(PROJECT_ID)).thenReturn(PROJECT_ID);
        AppUser actor = actor();
        when(authorization.requireProjectViewer(
                PROJECT_ID,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ)).thenReturn(actor);
        when(authorization.toolContext(actor, PROJECT_ID)).thenReturn(context(actor));
        when(summary.execute(any(FetchProjectSummaryTool.Parameters.class), any())).thenReturn(null);

        tools().getProjectSummary(PROJECT_ID);

        verify(authorization).requireProjectViewer(
                PROJECT_ID,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ);
        verify(summary).execute(eq(new FetchProjectSummaryTool.Parameters(PROJECT_ID)), any());
    }

    private ProjectReadMcpTools tools() {
        return new ProjectReadMcpTools(authorization, workItems, entries, relationships, blockers, summary);
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        return actor;
    }

    private ToolExecutionContext context(AppUser actor) {
        return new ToolExecutionContext(actor, null, java.util.List.of(PROJECT_ID));
    }
}
