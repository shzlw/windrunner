package com.windrunner.server.tools.work;

import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchEntryContextTool {
    private final WorkItemService workItems;
    private final EntryRepository entries;
    private final RelationshipRepository relationships;

    public LlmTool<EmptyInput> forEntry(String projectId, String workItemId) {
        return new LlmTool<>(
                "fetch_entry_context",
                "Fetch the parent WorkItem, related updates, and relationships for this entry when the supplied context is not enough.",
                EmptyInput.class,
                ignored -> {
                    WorkItem parent = workItems.get(projectId, workItemId);
                    List<EntrySummary> relatedEntries = entries.findPageByWorkItemId(
                                    workItemId, null, AiReviewLimits.MAX_RELATED_ENTRIES, 0)
                            .stream()
                            .map(entry -> new EntrySummary(entry.getId(), entry.getType(),
                                    AiReviewLimits.bounded(entry.getBody(), AiReviewLimits.MAX_TEXT_LENGTH),
                                    entry.getCreatedAt()))
                            .toList();
                    List<RelationshipSummary> relatedRelationships = relationships.findByEntity(
                                    projectId, "WORK_ITEM", workItemId, AiReviewLimits.MAX_RELATED_RELATIONSHIPS)
                            .stream()
                            .map(this::relationshipSummary)
                            .toList();
                    return new EntryContext(
                            new WorkItemSummary(parent.getId(), parent.getParentWorkItemId(), parent.getType(),
                                    parent.getTitle(), parent.getStatus(), parent.getDueDate(), parent.getPriority()),
                            relatedEntries, relatedRelationships);
                });
    }

    private RelationshipSummary relationshipSummary(Relationship relationship) {
        return new RelationshipSummary(relationship.getId(), relationship.getType(),
                relationship.getFromEntityType(), relationship.getFromEntityId(),
                relationship.getToEntityType(), relationship.getToEntityId(),
                AiReviewLimits.bounded(relationship.getReason(), AiReviewLimits.MAX_TEXT_LENGTH));
    }

    public record EmptyInput() { }

    public record EntryContext(WorkItemSummary parentWorkItem, List<EntrySummary> relatedEntries,
                               List<RelationshipSummary> relationships) { }

    public record WorkItemSummary(String id, String parentWorkItemId, String type, String title,
                                  String status, LocalDate dueDate, String priority) { }

    public record EntrySummary(String id, String type, String body, OffsetDateTime createdAt) { }

    public record RelationshipSummary(String id, String type, String fromEntityType, String fromEntityId,
                                      String toEntityType, String toEntityId, String reason) { }
}
