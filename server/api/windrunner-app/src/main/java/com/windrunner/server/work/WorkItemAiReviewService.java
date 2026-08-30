package com.windrunner.server.work;

import com.windrunner.server.llm.*;
import com.windrunner.server.llm.domain.LlmUsageFeature;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.api.WorkItemAiReviewRequest;
import com.windrunner.server.work.api.WorkItemAiReviewResponse;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class WorkItemAiReviewService {
    private static final String REVIEW_PROMPT = "work-item-ai-review.md";
    private final WorkItemService workItems;
    private final ProjectSearchService projectSearch;
    private final EntryRepository entries;
    private final RelationshipRepository relationships;
    private final LlmAvailabilityService llmAvailability;
    private final ObjectProvider<LlmService> llmServiceProvider;
    private final LlmUsageService llmUsageService;

    public WorkItemAiReviewResponse review(String projectId, String id, WorkItemAiReviewRequest request, String actorId) {
        WorkItem current = workItems.get(projectId, id);
        if (request == null || WorkItemService.blank(request.title()))
            throw WorkItemService.bad("Work item title is required");
        if (!llmAvailability.available())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI suggestions are unavailable");
        String type = WorkItemService.enumValue(request.type() == null ? current.getType() : request.type(), WorkTypes.WORK_ITEM_TYPES, "Work item type");
        String status = WorkItemService.enumValue(request.status() == null ? current.getStatus() : request.status(), WorkTypes.WORK_ITEM_STATUSES, "Work item status");
        List<Relationship> selectedRelationships = relationships.findByEntity(projectId, "WORK_ITEM", id, AiReviewLimits.MAX_RELATED_RELATIONSHIPS);
        Set<String> existingBlockerIds = selectedRelationships.stream()
                .filter(relationship -> "BLOCKED_BY".equals(relationship.getType()))
                .filter(relationship -> "WORK_ITEM".equals(relationship.getFromEntityType()) && id.equals(relationship.getFromEntityId()))
                .filter(relationship -> "WORK_ITEM".equals(relationship.getToEntityType()))
                .map(Relationship::getToEntityId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> availableBlockerIds = new HashSet<>();
        AtomicReference<Proposal> proposalRef = new AtomicReference<>();
        LlmTool<WorkItemLookup> fetchDetailsTool = new LlmTool<>(
                "fetch_work_item_details",
                "Fetch one related WorkItem with its direct children, recent updates, and relationships. Use only when the supplied WorkItem context is not enough.",
                WorkItemLookup.class,
                lookup -> {
                    WorkItemDetails details = fetchWorkItemDetails(projectId, lookup);
                    availableBlockerIds.add(details.workItem().id());
                    details.children().forEach(child -> availableBlockerIds.add(child.id()));
                    return details;
                }
        );
        LlmTool<WorkItemSearch> searchBlockerCandidatesTool = new LlmTool<>(
                "search_work_items_for_blocker",
                "Search this project for WorkItems that may be relevant blockers. Use a focused query derived from the current WorkItem; only returned IDs may be proposed as blockers.",
                WorkItemSearch.class,
                search -> {
                    List<WorkItemSummary> results = searchBlockerCandidates(projectId, id, search);
                    results.forEach(item -> availableBlockerIds.add(item.id()));
                    return results;
                }
        );
        LlmTool<Proposal> tool = new LlmTool<>("propose_work_item_revision", "Submit a conservative WorkItem revision.", Proposal.class, proposal -> {
            proposalRef.set(proposal);
            return Map.of("recorded", true);
        });
        LlmService llm = llmServiceProvider.getIfAvailable();
        if (llm == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI suggestions are unavailable");
        long startNanos = System.nanoTime();
        String reviewInput = input(request, id, type, status, existingBlockerIds);
        LlmResult<?> llmResult;
        try {
            llmResult = llm.runChatWithTools(
                    List.of(new LlmMessage("user", reviewInput)),
                    FileUtils.loadSystemPrompt(REVIEW_PROMPT),
                    List.of(fetchDetailsTool, searchBlockerCandidatesTool, tool));
        } catch (Exception exception) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            llmUsageService.recordFailure(
                    new LlmUsageContext(actorId, projectId, LlmUsageFeature.WORK_ITEM_AI_REVIEW),
                    exception.getMessage(),
                    durationMs);
            if (exception instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "The AI service is temporarily rate-limited. Please try the review again in a moment.", responseException);
            }
            throw exception;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        llmUsageService.record(
                new LlmUsageContext(actorId, projectId, LlmUsageFeature.WORK_ITEM_AI_REVIEW),
                llmResult,
                durationMs);
        Proposal proposal = proposalRef.get();
        if (proposal == null || WorkItemService.blank(proposal.proposedTitle()))
            throw new LlmException("AI did not return a WorkItem revision");
        String proposedType = WorkItemService.enumValue(blankOr(proposal.proposedType(), type), WorkTypes.WORK_ITEM_TYPES, "Proposed work item type");
        String proposedStatus = WorkItemService.enumValue(blankOr(proposal.proposedStatus(), status), WorkTypes.WORK_ITEM_STATUSES, "Proposed work item status");
        String proposedDueDate = validDate(blankOr(proposal.proposedDueDate(), LocalDate.now().plusDays(7).toString()));
        String proposedPriority = blankOr(proposal.proposedPriority(), request.priority());
        List<WorkItemAiReviewResponse.ProposedBlocker> proposedBlockers = proposal.proposedBlockers() == null ? List.of() : proposal.proposedBlockers().stream()
                .filter(candidate -> candidate != null && !WorkItemService.blank(candidate.workItemId()))
                .filter(candidate -> !id.equals(candidate.workItemId().trim()))
                .filter(candidate -> availableBlockerIds.contains(candidate.workItemId().trim()))
                .filter(candidate -> !existingBlockerIds.contains(candidate.workItemId().trim()))
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> candidate.workItemId().trim(),
                        candidate -> new WorkItemAiReviewResponse.ProposedBlocker(candidate.workItemId().trim(), WorkItemService.blank(candidate.reason()) ? null : candidate.reason().trim()),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                )).values().stream().toList();
        return new WorkItemAiReviewResponse(request.title().trim(), proposal.proposedTitle().trim(), proposedType, proposedStatus,
                proposedDueDate, WorkItemService.blank(proposedPriority) ? null : proposedPriority.trim().toUpperCase(),
                proposal.proposedAssignees() == null ? List.of() : proposal.proposedAssignees(), proposedBlockers,
                WorkItemService.blank(proposal.rationale()) ? null : proposal.rationale().trim());
    }

    private String input(WorkItemAiReviewRequest request, String workItemId, String type, String status, Set<String> existingBlockerIds) {
        return "Today: " + LocalDate.now() + "\nCurrent WorkItem id: " + workItemId + "\nTitle: " + AiReviewLimits.bounded(request.title().trim(), AiReviewLimits.MAX_TITLE_LENGTH) + "\nType: " + type + "\nStatus: " + status + "\nCurrent due date: " + blankOr(request.dueDate(), "Not set")
                + "\nPriority: " + blankOr(request.priority(), "Not set") + "\nCurrent assignees: " + (request.assignees() == null ? List.of() : request.assignees())
                + "\nExisting blocker WorkItem ids: " + existingBlockerIds
                + "\nAdditional context is available through fetch_work_item_details and search_work_items_for_blocker. Use those tools only when needed."
                + (WorkItemService.blank(request.instruction()) ? "" : "\n\nAuthor feedback:\n" + AiReviewLimits.bounded(request.instruction().trim(), AiReviewLimits.MAX_INSTRUCTION_LENGTH));
    }

    private WorkItemDetails fetchWorkItemDetails(String projectId, WorkItemLookup lookup) {
        if (lookup == null || WorkItemService.blank(lookup.workItemId())) {
            throw WorkItemService.bad("workItemId is required");
        }
        WorkItem item = workItems.get(projectId, lookup.workItemId().trim());
        List<Entry> itemEntries = entries.findPageByWorkItemId(item.getId(), null, AiReviewLimits.MAX_RELATED_ENTRIES, 0);
        List<Relationship> itemRelationships = relationships.findByEntity(projectId, "WORK_ITEM", item.getId(), AiReviewLimits.MAX_RELATED_RELATIONSHIPS);
        List<WorkItem> children = workItems.listSubtree(projectId, item.getId(), 1, AiReviewLimits.MAX_CHILDREN);
        return new WorkItemDetails(
                workItemSummary(item),
                children.stream().map(this::workItemSummary).toList(),
                itemEntries.stream().map(this::entrySummary).toList(),
                itemRelationships.stream().map(this::relationshipSummary).toList());
    }

    private List<WorkItemSummary> searchBlockerCandidates(String projectId, String currentWorkItemId, WorkItemSearch search) {
        if (search == null || WorkItemService.blank(search.query())) {
            return List.of();
        }
        return projectSearch.search(projectId, search.query(), AiReviewLimits.MAX_SEARCH_RESULTS).workItems().stream()
                .filter(item -> !currentWorkItemId.equals(item.getId()))
                .map(this::workItemSummary)
                .limit(AiReviewLimits.MAX_SEARCH_RESULTS)
                .toList();
    }

    private WorkItemSummary workItemSummary(WorkItem item) {
        return new WorkItemSummary(item.getId(), item.getParentWorkItemId(), AiReviewLimits.bounded(item.getTitle(), AiReviewLimits.MAX_TITLE_LENGTH), item.getType(), item.getStatus(), item.getDueDate(), item.getPriority());
    }

    private EntrySummary entrySummary(Entry entry) {
        return new EntrySummary(entry.getId(), entry.getType(), AiReviewLimits.bounded(entry.getBody(), AiReviewLimits.MAX_TEXT_LENGTH), entry.getCreatedAt());
    }

    private RelationshipSummary relationshipSummary(Relationship relationship) {
        return new RelationshipSummary(relationship.getId(), relationship.getType(), relationship.getFromEntityType(), relationship.getFromEntityId(), relationship.getToEntityType(), relationship.getToEntityId(), AiReviewLimits.bounded(relationship.getReason(), AiReviewLimits.MAX_TEXT_LENGTH));
    }

    public record WorkItemLookup(String workItemId) {
    }

    public record WorkItemSearch(String query) {
    }

    public record WorkItemDetails(WorkItemSummary workItem, List<WorkItemSummary> children,
                                  List<EntrySummary> updates, List<RelationshipSummary> relationships) {
    }

    public record WorkItemSummary(String id, String parentWorkItemId, String title, String type, String status,
                                  LocalDate dueDate, String priority) {
    }

    public record EntrySummary(String id, String type, String body, java.time.OffsetDateTime createdAt) {
    }

    public record RelationshipSummary(String id, String type, String fromEntityType, String fromEntityId,
                                      String toEntityType, String toEntityId, String reason) {
    }

    private String validDate(String value) {
        if (WorkItemService.blank(value)) return null;
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (Exception e) {
            throw WorkItemService.bad("Proposed due date is invalid");
        }
    }

    private String blankOr(String value, String fallback) {
        return WorkItemService.blank(value) ? fallback : value;
    }

    public record Proposal(String proposedTitle, String proposedType, String proposedStatus, String proposedDueDate,
                           String proposedPriority, List<WorkItemAssignee> proposedAssignees,
                           List<ProposedBlocker> proposedBlockers, String rationale) {
    }

    public record ProposedBlocker(String workItemId, String reason) {
    }
}
