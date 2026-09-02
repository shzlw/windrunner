package com.windrunner.server.tools.work;

import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.api.ProjectSearchResult;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewToolsTest {

    @Test
    void fetchesBoundedWorkItemDetailsAndRecordsReadIds() throws Exception {
        WorkItem root = workItem("root", "Root item");
        WorkItem child = workItem("child", "Child item");
        Entry entry = new Entry();
        entry.setId("entry-1");
        entry.setType("COMMENT");
        entry.setBody("e".repeat(AiReviewLimits.MAX_TEXT_LENGTH + 1));
        entry.setCreatedAt(OffsetDateTime.parse("2026-08-21T10:15:30Z"));
        Relationship relationship = relationship("relationship-1", "root", "child");
        List<String> readIds = new ArrayList<>();
        FetchWorkItemDetailsTool tool = new FetchWorkItemDetailsTool(
                workItems(root, List.of(child)), entries(List.of(entry)), relationships(List.of(relationship)));

        FetchWorkItemDetailsTool.WorkItemDetails result = (FetchWorkItemDetailsTool.WorkItemDetails)
                tool.forProject("project-1", readIds::add).handler().execute(
                        new FetchWorkItemDetailsTool.Parameters(" root "));

        assertThat(result.workItem().id()).isEqualTo("root");
        assertThat(result.children()).extracting(FetchWorkItemDetailsTool.WorkItemSummary::id)
                .containsExactly("child");
        assertThat(result.updates()).singleElement().satisfies(update -> {
            assertThat(update.id()).isEqualTo("entry-1");
            assertThat(update.body()).hasSize(AiReviewLimits.MAX_TEXT_LENGTH + 1);
            assertThat(update.body()).endsWith("…");
        });
        assertThat(result.relationships()).singleElement()
                .extracting(FetchWorkItemDetailsTool.RelationshipSummary::id)
                .isEqualTo("relationship-1");
        assertThat(readIds).containsExactly("root", "child");
    }

    @Test
    void fetchesBoundedEntryContext() throws Exception {
        WorkItem parent = workItem("parent", "Parent item");
        Entry relatedEntry = new Entry();
        relatedEntry.setId("entry-1");
        relatedEntry.setType("COMMENT");
        relatedEntry.setBody("r".repeat(AiReviewLimits.MAX_TEXT_LENGTH + 1));
        relatedEntry.setCreatedAt(OffsetDateTime.parse("2026-08-21T10:15:30Z"));
        Relationship relationship = relationship("relationship-1", "parent", "other");
        FetchEntryContextTool tool = new FetchEntryContextTool(
                workItems(parent, List.of()), entries(List.of(relatedEntry)), relationships(List.of(relationship)));

        FetchEntryContextTool.EntryContext result = (FetchEntryContextTool.EntryContext)
                tool.forEntry("project-1", "parent").handler().execute(new FetchEntryContextTool.EmptyInput());

        assertThat(result.parentWorkItem().id()).isEqualTo("parent");
        assertThat(result.parentWorkItem().title()).isEqualTo("Parent item");
        assertThat(result.relatedEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("entry-1");
            assertThat(entry.body()).hasSize(AiReviewLimits.MAX_TEXT_LENGTH + 1);
            assertThat(entry.body()).endsWith("…");
        });
        assertThat(result.relationships()).singleElement()
                .extracting(FetchEntryContextTool.RelationshipSummary::id)
                .isEqualTo("relationship-1");
    }

    @Test
    void searchesForBlockersExcludingTheCurrentWorkItemAndRecordsCandidates() throws Exception {
        WorkItem current = workItem("current", "Current item");
        WorkItem blocker = workItem("blocker", "Blocking item");
        WorkItemSearchProbe probe = new WorkItemSearchProbe(List.of(current, blocker));
        List<String> readIds = new ArrayList<>();
        SearchWorkItemsForBlockerTool tool = new SearchWorkItemsForBlockerTool(probe.service());

        @SuppressWarnings("unchecked")
        List<SearchWorkItemsForBlockerTool.WorkItemSummary> result =
                (List<SearchWorkItemsForBlockerTool.WorkItemSummary>) tool
                        .forProject("project-1", "current", readIds::add)
                        .handler().execute(new SearchWorkItemsForBlockerTool.Parameters("blocking"));

        assertThat(result).extracting(SearchWorkItemsForBlockerTool.WorkItemSummary::id)
                .containsExactly("blocker");
        assertThat(readIds).containsExactly("blocker");
        assertThat(probe.projectId).isEqualTo("project-1");
        assertThat(probe.query).isEqualTo("blocking");
        assertThat(probe.limit).isEqualTo(AiReviewLimits.MAX_SEARCH_RESULTS);
    }

    @Test
    void recordsWorkItemRevisionProposals() throws Exception {
        AtomicReference<ProposeWorkItemRevisionTool.Parameters> received = new AtomicReference<>();
        var tool = new ProposeWorkItemRevisionTool().forReview(received::set);
        WorkItemAssignee assignee = new WorkItemAssignee();
        assignee.setAssigneeType("USER");
        assignee.setAssigneeId("user-1");
        var proposal = new ProposeWorkItemRevisionTool.Parameters(
                "Updated title", "TASK", "IN_PROGRESS", "2026-08-30", "HIGH",
                List.of(assignee), List.of(new ProposeWorkItemRevisionTool.ProposedBlocker("blocker", "Dependency")),
                "Clearer scope");

        assertThat(tool.handler().execute(proposal)).isEqualTo(java.util.Map.of("recorded", true));
        assertThat(received).hasValue(proposal);
        assertThat(tool.name()).isEqualTo("propose_work_item_revision");
    }

    @Test
    void recordsEntryRevisionProposals() throws Exception {
        AtomicReference<ProposeEntryRevisionTool.Parameters> received = new AtomicReference<>();
        var tool = new ProposeEntryRevisionTool().forReview(received::set);
        var proposal = new ProposeEntryRevisionTool.Parameters("Revised body", "DECISION", "More precise");

        assertThat(tool.handler().execute(proposal)).isEqualTo(java.util.Map.of("recorded", true));
        assertThat(received).hasValue(proposal);
        assertThat(tool.name()).isEqualTo("propose_entry_revision");
    }

    private WorkItemService workItems(WorkItem item, List<WorkItem> children) {
        return new WorkItemService(null, null, null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public WorkItem get(String projectId, String id) {
                return item;
            }

            @Override
            public List<WorkItem> listSubtree(String projectId, String rootWorkItemId, int maxDepth, int limit) {
                return children;
            }
        };
    }

    private EntryRepository entries(List<Entry> values) {
        return (EntryRepository) Proxy.newProxyInstance(
                EntryRepository.class.getClassLoader(), new Class<?>[]{EntryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findPageByWorkItemId" -> values;
                    case "toString" -> "EntryRepositoryProbe";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private RelationshipRepository relationships(List<Relationship> values) {
        return (RelationshipRepository) Proxy.newProxyInstance(
                RelationshipRepository.class.getClassLoader(), new Class<?>[]{RelationshipRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByEntity" -> values;
                    case "toString" -> "RelationshipRepositoryProbe";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private WorkItem workItem(String id, String title) {
        WorkItem item = new WorkItem();
        item.setId(id);
        item.setProjectId("project-1");
        item.setParentWorkItemId("parent");
        item.setType("TASK");
        item.setTitle(title);
        item.setStatus("TODO");
        item.setDueDate(LocalDate.of(2026, 8, 30));
        item.setPriority("HIGH");
        return item;
    }

    private Relationship relationship(String id, String fromId, String toId) {
        Relationship relationship = new Relationship();
        relationship.setId(id);
        relationship.setType("BLOCKED_BY");
        relationship.setFromEntityType("WORK_ITEM");
        relationship.setFromEntityId(fromId);
        relationship.setToEntityType("WORK_ITEM");
        relationship.setToEntityId(toId);
        relationship.setReason("Dependency");
        return relationship;
    }

    private static final class WorkItemSearchProbe {
        private final List<WorkItem> results;
        private String projectId;
        private String query;
        private Integer limit;

        private WorkItemSearchProbe(List<WorkItem> results) {
            this.results = results;
        }

        private ProjectSearchService service() {
            return new ProjectSearchService(null, null, null, null) {
                @Override
                public ProjectSearchResult search(String project, String text, Integer maxResults) {
                    projectId = project;
                    query = text;
                    limit = maxResults;
                    return new ProjectSearchResult(results, List.of(), List.of());
                }
            };
        }
    }
}
