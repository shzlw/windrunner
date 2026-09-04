package com.windrunner.server.work;

import com.windrunner.server.llm.*;
import com.windrunner.server.llm.domain.LlmUsageFeature;
import com.windrunner.server.tools.work.FetchWorkItemDetailsTool;
import com.windrunner.server.tools.work.ProposeWorkItemRevisionTool;
import com.windrunner.server.tools.work.SearchWorkItemsForBlockerTool;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.api.WorkItemAiReviewRequest;
import com.windrunner.server.work.api.WorkItemAiReviewResponse;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class WorkItemAiReviewService {
    private static final String REVIEW_PROMPT = "work-item-ai-review.md";
    private final WorkItemService workItems;
    private final RelationshipRepository relationships;
    private final FetchWorkItemDetailsTool fetchWorkItemDetailsTool;
    private final SearchWorkItemsForBlockerTool searchWorkItemsForBlockerTool;
    private final ProposeWorkItemRevisionTool proposeWorkItemRevisionTool;
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
        Set<String> availableBlockerIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        AtomicReference<ProposeWorkItemRevisionTool.Parameters> proposalRef = new AtomicReference<>();
        LlmTool<?> fetchDetailsTool = fetchWorkItemDetailsTool.forProject(projectId, availableBlockerIds::add);
        LlmTool<?> searchBlockerCandidatesTool = searchWorkItemsForBlockerTool.forProject(
                projectId, id, availableBlockerIds::add);
        LlmTool<?> tool = proposeWorkItemRevisionTool.forReview(proposalRef::set);
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
        ProposeWorkItemRevisionTool.Parameters proposal = proposalRef.get();
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

}
