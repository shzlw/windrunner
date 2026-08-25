package com.windrunner.server.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkItemWriteMcpToolsTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String ACTOR_ID = "user-key-owner";

    @Mock
    private WorkItemService workItems;
    @Mock
    private EntryService entries;
    @Mock
    private RelationshipService relationships;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUpContext() {
        AppUser actor = new AppUser();
        actor.setId(ACTOR_ID);
        // Lenient: each test exercises only one scope.
        lenient().when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE)).thenReturn(actor);
        lenient().when(externalAccessService.requireScope(request, ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(actor);
        lenient().when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(actor);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private WorkItemWriteMcpTools controller() {
        return new WorkItemWriteMcpTools(
                workItems, entries, relationships, externalAccessService, projectAccessService);
    }

    @Test
    void addEntryRequiresEntriesWriteAndEditorAndDelegates() {
        Entry created = new Entry();
        created.setId("entr-1");
        created.setWorkItemId("witm-1");
        created.setType("COMMENT");
        when(entries.create(eq(PROJECT_ID), any(Entry.class), eq(ACTOR_ID))).thenReturn(created);

        WorkItemWriteMcpTools.AddedEntry response =
                controller().addEntry(PROJECT_ID, "witm-1", "Found the root cause.", "EVIDENCE");

        ArgumentCaptor<Entry> captured = ArgumentCaptor.forClass(Entry.class);
        verify(entries).create(eq(PROJECT_ID), captured.capture(), eq(ACTOR_ID));
        assertThat(captured.getValue().getWorkItemId()).isEqualTo("witm-1");
        assertThat(captured.getValue().getType()).isEqualTo("EVIDENCE");
        verify(projectAccessService).requireProjectRole(
                eq(PROJECT_ID), actorWithId(ACTOR_ID), eq(ProjectRoles.EDITOR));
        assertThat(response.id()).isEqualTo("entr-1");
    }

    @Test
    void addEntryDefaultsTypeToComment() {
        Entry created = new Entry();
        created.setId("entr-2");
        when(entries.create(eq(PROJECT_ID), any(Entry.class), eq(ACTOR_ID))).thenReturn(created);

        controller().addEntry(PROJECT_ID, "witm-1", "A note.", null);

        ArgumentCaptor<Entry> captured = ArgumentCaptor.forClass(Entry.class);
        verify(entries).create(eq(PROJECT_ID), captured.capture(), eq(ACTOR_ID));
        assertThat(captured.getValue().getType()).isEqualTo("COMMENT");
    }

    @Test
    void addEntryRejectsBlankBody() {
        assertThatThrownBy(() -> controller().addEntry(PROJECT_ID, "witm-1", "  ", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(entries, never()).create(any(), any(), any());
    }

    @Test
    void updateStatusDelegatesToService() {
        WorkItem updated = new WorkItem();
        updated.setId("witm-1");
        updated.setTitle("Fix login");
        updated.setStatus("DONE");
        when(workItems.updateStatus(PROJECT_ID, "witm-1", "DONE", ACTOR_ID)).thenReturn(updated);

        WorkItemWriteMcpTools.StatusResult response =
                controller().updateWorkItemStatus(PROJECT_ID, "witm-1", "DONE");

        verify(projectAccessService).requireProjectRole(
                eq(PROJECT_ID), actorWithId(ACTOR_ID), eq(ProjectRoles.EDITOR));
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.title()).isEqualTo("Fix login");
    }

    @Test
    void createWorkItemMapsOptionalFields() {
        WorkItem requested = new WorkItem();
        requested.setTitle("Investigate flaky tests");
        requested.setType("TASK");
        requested.setParentWorkItemId("witm-parent");
        requested.setPriority("HIGH");
        requested.setDueDate(LocalDate.parse("2026-09-30"));
        WorkItem saved = new WorkItem();
        saved.setId("witm-new");
        saved.setProjectId(PROJECT_ID);
        saved.setParentWorkItemId("witm-parent");
        saved.setType("TASK");
        saved.setTitle("Investigate flaky tests");
        saved.setStatus("OPEN");
        saved.setPriority("HIGH");
        saved.setDueDate(LocalDate.parse("2026-09-30"));
        when(workItems.create(eq(PROJECT_ID), any(WorkItem.class), eq(List.of()), eq(ACTOR_ID))).thenReturn(saved);

        WorkItemWriteMcpTools.CreatedWorkItem response = controller().createWorkItem(
                PROJECT_ID, "Investigate flaky tests", "task", "witm-parent", "high", "2026-09-30");

        ArgumentCaptor<WorkItem> captured = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItems).create(eq(PROJECT_ID), captured.capture(), eq(List.of()), eq(ACTOR_ID));
        assertThat(captured.getValue().getTitle()).isEqualTo("Investigate flaky tests");
        assertThat(captured.getValue().getParentWorkItemId()).isEqualTo("witm-parent");
        assertThat(captured.getValue().getDueDate()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(response.id()).isEqualTo("witm-new");
    }

    @Test
    void createWorkItemRejectsInvalidDueDate() {
        assertThatThrownBy(() -> controller().createWorkItem(
                PROJECT_ID, "Bad date", null, null, null, "not-a-date"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(workItems, never()).create(any(), any(), any(), any());
    }

    @Test
    void linkRelationshipRequiresRelationshipsWriteAndDelegates() {
        Relationship created = new Relationship();
        created.setId("rela-1");
        created.setType("BLOCKED_BY");
        created.setFromEntityId("witm-1");
        created.setToEntityId("witm-2");
        when(relationships.create(eq(PROJECT_ID), any(Relationship.class), eq(ACTOR_ID))).thenReturn(created);

        WorkItemWriteMcpTools.CreatedRelationship response = controller().linkRelationship(
                PROJECT_ID, "BLOCKED_BY", "witm-1", "witm-2", "needs deploy first");

        ArgumentCaptor<Relationship> captured = ArgumentCaptor.forClass(Relationship.class);
        verify(relationships).create(eq(PROJECT_ID), captured.capture(), eq(ACTOR_ID));
        assertThat(captured.getValue().getFromEntityType()).isEqualTo("WORK_ITEM");
        assertThat(captured.getValue().getToEntityType()).isEqualTo("WORK_ITEM");
        assertThat(response.type()).isEqualTo("BLOCKED_BY");
    }

    private AppUser actorWithId(String id) {
        return org.mockito.ArgumentMatchers.argThat(actor -> actor != null && id.equals(actor.getId()));
    }
}
