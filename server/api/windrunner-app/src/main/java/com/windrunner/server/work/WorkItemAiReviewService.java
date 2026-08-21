package com.windrunner.server.work;

import com.windrunner.server.llm.LlmAvailabilityService;
import com.windrunner.server.llm.LlmException;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.llm.LlmUsageContext;
import com.windrunner.server.llm.LlmUsageService;
import com.windrunner.server.llm.domain.LlmUsageFeature;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.api.WorkItemAiReviewRequest;
import com.windrunner.server.work.api.WorkItemAiReviewResponse;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WorkItemAiReviewService {
    private static final String REVIEW_PROMPT = "work-item-ai-review.md";
    private final WorkItemService workItems;
    private final EntryService entries;
    private final RelationshipService relationships;
    private final LlmAvailabilityService llmAvailability;
    private final ObjectProvider<LlmService> llmServiceProvider;
    private final LlmUsageService llmUsageService;

    public WorkItemAiReviewResponse review(String projectId, String id, WorkItemAiReviewRequest request, String actorId) {
        WorkItem current = workItems.get(projectId, id);
        if (request == null || WorkItemService.blank(request.title())) throw WorkItemService.bad("Work item title is required");
        if (!llmAvailability.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI suggestions are unavailable");
        String type = WorkItemService.enumValue(request.type() == null ? current.getType() : request.type(), WorkTypes.WORK_ITEM_TYPES, "Work item type");
        String status = WorkItemService.enumValue(request.status() == null ? current.getStatus() : request.status(), WorkTypes.WORK_ITEM_STATUSES, "Work item status");
        List<WorkItem> candidates = workItems.list(projectId).stream().filter(item -> !id.equals(item.getId())).toList();
        Set<String> existingBlockerIds = relationships.list(projectId).stream()
                .filter(relationship -> "BLOCKED_BY".equals(relationship.getType()))
                .filter(relationship -> "WORK_ITEM".equals(relationship.getFromEntityType()) && id.equals(relationship.getFromEntityId()))
                .filter(relationship -> "WORK_ITEM".equals(relationship.getToEntityType()))
                .map(Relationship::getToEntityId)
                .collect(java.util.stream.Collectors.toSet());
        AtomicReference<Proposal> proposalRef = new AtomicReference<>();
        LlmTool<Proposal> tool = new LlmTool<>("propose_work_item_revision", "Submit a conservative WorkItem revision.", Proposal.class, proposal -> { proposalRef.set(proposal); return Map.of("recorded", true); });
        LlmService llm = llmServiceProvider.getIfAvailable();
        if (llm == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI suggestions are unavailable");
        long startNanos = System.nanoTime();
        LlmResult<?> llmResult = llm.runChatWithTools(List.of(new LlmMessage("user", input(request, type, status, candidates, entries.list(projectId).stream().filter(entry -> id.equals(entry.getWorkItemId())).toList(), existingBlockerIds))), FileUtils.loadSystemPrompt(REVIEW_PROMPT), List.of(tool));
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        llmUsageService.record(
                new LlmUsageContext(actorId, projectId, LlmUsageFeature.WORK_ITEM_AI_REVIEW),
                llmResult,
                durationMs);
        Proposal proposal = proposalRef.get();
        if (proposal == null || WorkItemService.blank(proposal.proposedTitle())) throw new LlmException("AI did not return a WorkItem revision");
        String proposedType = WorkItemService.enumValue(blankOr(proposal.proposedType(), type), WorkTypes.WORK_ITEM_TYPES, "Proposed work item type");
        String proposedStatus = WorkItemService.enumValue(blankOr(proposal.proposedStatus(), status), WorkTypes.WORK_ITEM_STATUSES, "Proposed work item status");
        String proposedDueDate = validDate(blankOr(proposal.proposedDueDate(), LocalDate.now().plusDays(7).toString()));
        String proposedPriority = blankOr(proposal.proposedPriority(), request.priority());
        Map<String, WorkItem> candidatesById = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesById.put(candidate.getId(), candidate));
        List<WorkItemAiReviewResponse.ProposedBlocker> proposedBlockers = proposal.proposedBlockers() == null ? List.of() : proposal.proposedBlockers().stream()
                .filter(candidate -> candidate != null && !WorkItemService.blank(candidate.workItemId()))
                .filter(candidate -> candidatesById.containsKey(candidate.workItemId().trim()))
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

    private String input(WorkItemAiReviewRequest request, String type, String status, List<WorkItem> candidates, List<Entry> itemEntries, Set<String> existingBlockerIds) {
        return "Today: " + LocalDate.now() + "\nTitle: " + request.title().trim() + "\nType: " + type + "\nStatus: " + status + "\nCurrent due date: " + blankOr(request.dueDate(), "Not set")
                + "\nPriority: " + blankOr(request.priority(), "Not set") + "\nCurrent assignees: " + (request.assignees() == null ? List.of() : request.assignees())
                + "\nCurrent updates: " + itemEntries
                + "\nExisting blocker WorkItem ids: " + existingBlockerIds
                + "\nCandidate WorkItems for blockers (only these ids may be proposed): " + candidates
                + (WorkItemService.blank(request.instruction()) ? "" : "\n\nAuthor feedback:\n" + request.instruction().trim());
    }
    private String validDate(String value) { if (WorkItemService.blank(value)) return null; try { return LocalDate.parse(value.trim()).toString(); } catch (Exception e) { throw WorkItemService.bad("Proposed due date is invalid"); } }
    private String blankOr(String value, String fallback) { return WorkItemService.blank(value) ? fallback : value; }
    public record Proposal(String proposedTitle, String proposedType, String proposedStatus, String proposedDueDate, String proposedPriority, List<WorkItemAssignee> proposedAssignees, List<ProposedBlocker> proposedBlockers, String rationale) { }
    public record ProposedBlocker(String workItemId, String reason) { }
}
