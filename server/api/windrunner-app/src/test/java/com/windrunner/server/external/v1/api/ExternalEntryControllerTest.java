package com.windrunner.server.external.v1.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalEntryResponse;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
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
class ExternalEntryControllerTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String WORK_ITEM_ID = "witm-1";
    private static final String ACTOR_ID = "user-key-owner";

    @Mock
    private EntryService entries;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private WorkItemRepository workItems;
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

    private WorkItem workItem() {
        WorkItem item = new WorkItem();
        item.setId(WORK_ITEM_ID);
        item.setProjectId(PROJECT_ID);
        return item;
    }

    private Entry entry(String id, String projectId, String workItemId) {
        Entry entry = new Entry();
        entry.setId(id);
        entry.setProjectId(projectId);
        entry.setWorkItemId(workItemId);
        return entry;
    }

    @Test
    void listRequiresViewerOnParentWorkItemProjectAndReturnsPage() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_READ)).thenReturn(actor());
        when(workItems.findById(WORK_ITEM_ID)).thenReturn(Optional.of(workItem()));
        List<Entry> items = List.of(entry("entr-1", PROJECT_ID, WORK_ITEM_ID));
        OffsetDateTime after = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        when(entryRepository.findPageByWorkItemId(WORK_ITEM_ID, after, 25, 0L)).thenReturn(items);
        when(entryRepository.countByWorkItemId(WORK_ITEM_ID, after)).thenReturn(1L);

        ApiResponse<List<ExternalEntryResponse>> response = controller().list(WORK_ITEM_ID, 0, 25, after, request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.VIEWER);
        assertThat(response.data()).containsExactlyElementsOf(
                items.stream().map(ExternalEntryResponse::from).toList());
        assertThat(response.meta().totalItems()).isEqualTo(1L);
    }

    @Test
    void listUnknownWorkItemReturns404WithoutAuthorizationCheck() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_READ)).thenReturn(actor());
        when(workItems.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().list("missing", 0, 50, null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(projectAccessService, entryRepository);
    }

    @Test
    void createSetsWorkItemIdFromPathAndRequiresEditor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE)).thenReturn(actor());
        when(workItems.findById(WORK_ITEM_ID)).thenReturn(Optional.of(workItem()));
        Entry body = entry(null, "spoiled-project", "spoiled-work-item");
        Entry created = entry("entr-new", PROJECT_ID, WORK_ITEM_ID);
        when(entries.create(eq(PROJECT_ID), any(Entry.class), eq(ACTOR_ID))).thenReturn(created);

        ApiResponse<ExternalEntryResponse> response = controller().create(WORK_ITEM_ID, body, request);

        org.mockito.ArgumentCaptor<Entry> captured = org.mockito.ArgumentCaptor.forClass(Entry.class);
        verify(entries).create(eq(PROJECT_ID), captured.capture(), eq(ACTOR_ID));
        // The path variable wins over anything supplied in the request body.
        assertThat(captured.getValue().getWorkItemId()).isEqualTo(WORK_ITEM_ID);
        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(ExternalEntryResponse.from(created));
    }

    @Test
    void createRejectsNullBody() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE)).thenReturn(actor());
        when(workItems.findById(WORK_ITEM_ID)).thenReturn(Optional.of(workItem()));

        assertThatThrownBy(() -> controller().create(WORK_ITEM_ID, null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(entries, never()).create(anyString(), any(Entry.class), anyString());
    }

    @Test
    void updateResolvesProjectFromEntryAndRequiresEditor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE)).thenReturn(actor());
        Entry current = entry("entr-1", "project-other", WORK_ITEM_ID);
        when(entryRepository.findById("entr-1")).thenReturn(Optional.of(current));
        Entry updated = entry("entr-1", "project-other", WORK_ITEM_ID);
        when(entries.update("project-other", "entr-1", current, ACTOR_ID)).thenReturn(updated);

        ApiResponse<ExternalEntryResponse> response = controller().update("entr-1", current, request);

        verify(projectAccessService).requireProjectRole("project-other", actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(ExternalEntryResponse.from(updated));
    }

    @Test
    void deleteResolvesProjectFromEntryAndRequiresEditor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE)).thenReturn(actor());
        Entry current = entry("entr-1", "project-other", WORK_ITEM_ID);
        when(entryRepository.findById("entr-1")).thenReturn(Optional.of(current));

        controller().delete("entr-1", request);

        verify(projectAccessService).requireProjectRole("project-other", actor(), ProjectRoles.EDITOR);
        verify(entries).delete("project-other", "entr-1", ACTOR_ID);
    }

    private ExternalEntryController controller() {
        return new ExternalEntryController(entries, entryRepository, workItems, externalAccessService, projectAccessService);
    }
}
