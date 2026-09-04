package com.windrunner.server.tools.work;

import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class FetchWorkItemDetailsTool {
    private final WorkItemService workItems;
    private final EntryRepository entries;
    private final RelationshipRepository relationships;

    /**
     * The callback may be invoked concurrently when multiple detail calls are
     * returned in one model response, so callers must provide a thread-safe callback.
     */
    public LlmTool<Parameters> forProject(String projectId, Consumer<String> onWorkItemRead) {
        Objects.requireNonNull(onWorkItemRead, "Work item read callback is required");
        return new LlmTool<>(
                "fetch_work_item_details",
                "Fetch one related WorkItem with its direct children, recent updates, and relationships. Use only when the supplied WorkItem context is not enough.",
                Parameters.class,
                parameters -> {
                    if (parameters == null || blank(parameters.workItemId())) {
                        throw bad("workItemId is required");
                    }
                    WorkItem item = workItems.get(projectId, parameters.workItemId().trim());
                    onWorkItemRead.accept(item.getId());
                    List<Entry> itemEntries = entries.findPageByWorkItemId(
                            item.getId(), null, AiReviewLimits.MAX_RELATED_ENTRIES, 0);
                    List<Relationship> itemRelationships = relationships.findByEntity(
                            projectId, "WORK_ITEM", item.getId(), AiReviewLimits.MAX_RELATED_RELATIONSHIPS);
                    List<WorkItem> children = workItems.listSubtree(
                            projectId, item.getId(), 1, AiReviewLimits.MAX_CHILDREN);
                    children.forEach(child -> onWorkItemRead.accept(child.getId()));
                    return new WorkItemDetails(
                            workItemSummary(item),
                            children.stream().map(this::workItemSummary).toList(),
                            itemEntries.stream().map(this::entrySummary).toList(),
                            itemRelationships.stream().map(this::relationshipSummary).toList());
                },
                true);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private WorkItemSummary workItemSummary(WorkItem item) {
        return new WorkItemSummary(item.getId(), item.getParentWorkItemId(),
                AiReviewLimits.bounded(item.getTitle(), AiReviewLimits.MAX_TITLE_LENGTH),
                item.getType(), item.getStatus(), item.getDueDate(), item.getPriority());
    }

    private EntrySummary entrySummary(Entry entry) {
        return new EntrySummary(entry.getId(), entry.getType(),
                AiReviewLimits.bounded(entry.getBody(), AiReviewLimits.MAX_TEXT_LENGTH), entry.getCreatedAt());
    }

    private RelationshipSummary relationshipSummary(Relationship relationship) {
        return new RelationshipSummary(relationship.getId(), relationship.getType(),
                relationship.getFromEntityType(), relationship.getFromEntityId(),
                relationship.getToEntityType(), relationship.getToEntityId(),
                AiReviewLimits.bounded(relationship.getReason(), AiReviewLimits.MAX_TEXT_LENGTH));
    }

    public record Parameters(String workItemId) { }

    public record WorkItemDetails(WorkItemSummary workItem, List<WorkItemSummary> children,
                                  List<EntrySummary> updates, List<RelationshipSummary> relationships) { }

    public record WorkItemSummary(String id, String parentWorkItemId, String title, String type, String status,
                                  LocalDate dueDate, String priority) { }

    public record EntrySummary(String id, String type, String body, java.time.OffsetDateTime createdAt) { }

    public record RelationshipSummary(String id, String type, String fromEntityType, String fromEntityId,
                                      String toEntityType, String toEntityId, String reason) { }
}
