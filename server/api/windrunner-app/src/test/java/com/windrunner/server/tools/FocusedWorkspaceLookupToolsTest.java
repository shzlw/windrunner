package com.windrunner.server.tools;

import com.windrunner.server.search.SearchNormalizer;
import com.windrunner.server.tools.work.FindRelationshipsExactTool;
import com.windrunner.server.tools.work.SearchEntriesTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FocusedWorkspaceLookupToolsTest {

    private static final String PROJECT_ID = "project-1";

    @Mock
    private EntryRepository entries;
    @Mock
    private RelationshipRepository relationships;
    @Mock
    private SearchNormalizer searchNormalizer;
    @Mock
    private ToolAuthorizationService authorization;

    @Test
    void searchEntriesUsesExactModeForDuplicateChecks() {
        authorizeProjectRead();
        Entry entry = new Entry();
        entry.setId("entry-1");
        when(entries.findExactPageByProjectAndWorkItemId(
                PROJECT_ID, "item-1", "Same body", 20, 0L)).thenReturn(List.of(entry));
        when(entries.countExactByProjectAndWorkItemId(PROJECT_ID, "item-1", "Same body")).thenReturn(1L);

        SearchEntriesTool.Response response = (SearchEntriesTool.Response) new SearchEntriesTool(
                entries, searchNormalizer, authorization).execute(
                new SearchEntriesTool.Parameters(PROJECT_ID, " item-1 ", " Same body ", true, null, null), context());

        assertThat(response.exact()).isTrue();
        assertThat(response.entries()).extracting(SearchEntriesTool.EntryResult::id)
                .containsExactly("entry-1");
        assertThat(response.total()).isOne();
        verify(searchNormalizer, never()).normalize(any());
    }

    @Test
    void searchEntriesUsesBoundedRankedSearchAndPaging() {
        authorizeProjectRead();
        Entry entry = new Entry();
        entry.setId("entry-1");
        when(searchNormalizer.normalize("deployment")).thenReturn("deploy");
        when(entries.searchPageInProject(PROJECT_ID, "item-1", "deploy", "deployment", 10, 10L))
                .thenReturn(List.of(entry));
        when(entries.countSearchInProject(PROJECT_ID, "item-1", "deploy", "deployment")).thenReturn(11L);

        SearchEntriesTool.Response response = (SearchEntriesTool.Response) new SearchEntriesTool(
                entries, searchNormalizer, authorization).execute(
                new SearchEntriesTool.Parameters(PROJECT_ID, "item-1", "deployment", false, 10, 10), context());

        assertThat(response.total()).isEqualTo(11);
        assertThat(response.offset()).isEqualTo(10);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void exactRelationshipLookupNormalizesAndReportsMatches() {
        authorizeProjectRead();
        Relationship relationship = new Relationship();
        relationship.setId("relationship-1");
        when(relationships.findExactPage(PROJECT_ID, "WORK_ITEM", "from-1", "ENTRY", "to-1",
                "SUPPORTS", 100, 0L)).thenReturn(List.of(relationship));
        when(relationships.countExact(PROJECT_ID, "WORK_ITEM", "from-1", "ENTRY", "to-1", "SUPPORTS"))
                .thenReturn(1L);

        FindRelationshipsExactTool.Response response = (FindRelationshipsExactTool.Response)
                new FindRelationshipsExactTool(relationships, authorization).execute(
                        new FindRelationshipsExactTool.Parameters(PROJECT_ID, "work_item", " from-1 ",
                                "entry", "to-1", "supports"), context());

        assertThat(response.relationships()).extracting(FindRelationshipsExactTool.RelationshipResult::id)
                .containsExactly("relationship-1");
        assertThat(response.total()).isOne();
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void exactRelationshipLookupRejectsInvalidRelationshipType() {
        authorizeProjectRead();

        assertThatThrownBy(() -> new FindRelationshipsExactTool(relationships, authorization).execute(
                new FindRelationshipsExactTool.Parameters(PROJECT_ID, "WORK_ITEM", "from-1",
                        "WORK_ITEM", "to-1", "NOT_A_TYPE"), context()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("relationshipType is invalid");
        verify(relationships, never()).findExactPage(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());
    }

    private void authorizeProjectRead() {
        when(authorization.requireProject(any(), any()))
                .thenAnswer(invocation -> ((ToolExecutionContext) invocation.getArgument(0))
                        .requireProjectId((String) invocation.getArgument(1)));
    }

    private static ToolExecutionContext context() {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        return new ToolExecutionContext(actor, "session-1", List.of(PROJECT_ID));
    }
}
