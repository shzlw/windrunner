package com.windrunner.server.work;

import com.windrunner.server.audit.AuditActions;
import com.windrunner.server.audit.AuditEntityTypes;
import com.windrunner.server.audit.AuditLogEntry;
import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.audit.AuditOutcomes;
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
import com.windrunner.server.work.api.EntryAiReviewDecisionRequest;
import com.windrunner.server.work.api.EntryAiCreateReviewDecisionRequest;
import com.windrunner.server.work.api.EntryAiNewReviewRequest;
import com.windrunner.server.work.api.EntryAiReviewResponse;
import com.windrunner.server.work.domain.Entry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EntryAiReviewService {

    private static final String REVIEW_PROMPT = "entry-ai-review.md";

    private final EntryService entries;
    private final WorkItemService workItems;
    private final AuditLogService auditLogService;
    private final LlmAvailabilityService llmAvailability;
    private final ObjectProvider<LlmService> llmServiceProvider;
    private final LlmUsageService llmUsageService;

    public EntryAiReviewResponse review(String projectId, String entryId, String body, String type, String instruction, String actorId) {
        Entry entry = entries.get(projectId, entryId);
        return reviewBody(projectId, entry.getWorkItemId(), body, type == null ? entry.getType() : type, instruction, actorId);
    }

    public EntryAiReviewResponse reviewNew(String projectId, EntryAiNewReviewRequest review, String actorId) {
        if (review == null || WorkItemService.blank(review.workItemId())) {
            throw WorkItemService.bad("Entry workItemId is required");
        }
        return reviewBody(projectId, review.workItemId(), review.body(), review.type(), review.instruction(), actorId);
    }

    private EntryAiReviewResponse reviewBody(String projectId, String workItemId, String body, String type, String instruction, String actorId) {
        String originalBody = requiredBody(body);
        String currentType = WorkItemService.enumValue(type == null ? "COMMENT" : type, WorkTypes.ENTRY_TYPES, "Entry type");
        var parent = workItems.get(projectId, workItemId);
        if (!llmAvailability.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI suggestions are unavailable");
        }

        AtomicReference<Proposal> proposalRef = new AtomicReference<>();
        LlmTool<Proposal> tool = new LlmTool<>(
                "propose_entry_revision",
                "Submit the reviewed entry body, proposed entry type, and a concise rationale for the revision.",
                Proposal.class,
                proposal -> {
                    proposalRef.set(proposal);
                    return Map.of("recorded", true);
                }
        );
        LlmService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI suggestions are unavailable");
        }
        long startNanos = System.nanoTime();
        LlmResult<?> llmResult = llmService.runChatWithTools(
                List.of(new LlmMessage("user", reviewInput(parent.getType(), parent.getTitle(), currentType, originalBody, instruction))),
                FileUtils.loadSystemPrompt(REVIEW_PROMPT),
                List.of(tool)
        );
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        llmUsageService.record(
                new LlmUsageContext(actorId, projectId, LlmUsageFeature.ENTRY_AI_REVIEW),
                llmResult,
                durationMs);
        Proposal proposal = proposalRef.get();
        if (proposal == null || WorkItemService.blank(proposal.proposedBody())) {
            throw new LlmException("AI did not return an entry revision");
        }
        String proposedBody = proposal.proposedBody().trim();
        String proposedType = WorkItemService.blank(proposal.proposedType())
                ? currentType
                : WorkItemService.enumValue(proposal.proposedType(), WorkTypes.ENTRY_TYPES, "Proposed entry type");
        String rationale = WorkItemService.blank(proposal.rationale()) ? null : proposal.rationale().trim();
        return new EntryAiReviewResponse(originalBody, proposedBody, proposedType, rationale);
    }

    @Transactional
    public Entry accept(String projectId, String entryId, EntryAiReviewDecisionRequest decision, String actorId) {
        Entry current = entries.get(projectId, entryId);
        Decision values = decisionValues(decision);
        Entry update = new Entry();
        update.setWorkItemId(current.getWorkItemId());
        update.setType(WorkItemService.blank(decision.type()) ? current.getType() : decision.type());
        update.setBody(values.proposedBody());
        Entry updated = entries.update(projectId, entryId, update, actorId);
        auditLogService.logAfterCommit(aiAudit(actorId, AuditActions.AI_ACCEPT, current, values, "ACCEPTED"));
        return updated;
    }

    public void reject(String projectId, String entryId, EntryAiReviewDecisionRequest decision, String actorId) {
        Entry current = entries.get(projectId, entryId);
        Decision values = decisionValues(decision);
        auditLogService.logImmediately(aiAudit(actorId, AuditActions.AI_REJECT, current, values, "REJECTED"));
    }

    @Transactional
    public Entry acceptNew(String projectId, EntryAiCreateReviewDecisionRequest decision, String actorId) {
        CreateDecision values = createDecisionValues(decision);
        Entry entry = new Entry();
        entry.setWorkItemId(values.workItemId());
        entry.setType(values.type());
        entry.setBody(values.proposedBody());
        Entry created = entries.create(projectId, entry, actorId);
        auditLogService.logAfterCommit(aiAudit(actorId, AuditActions.AI_ACCEPT, created,
                new Decision(values.originalBody(), values.proposedBody()), "ACCEPTED"));
        return created;
    }

    public void rejectNew(String projectId, EntryAiCreateReviewDecisionRequest decision, String actorId) {
        CreateDecision values = createDecisionValues(decision);
        Entry draft = new Entry();
        draft.setProjectId(projectId);
        draft.setWorkItemId(values.workItemId());
        auditLogService.logImmediately(aiAudit(actorId, AuditActions.AI_REJECT, draft,
                new Decision(values.originalBody(), values.proposedBody()), "REJECTED"));
    }

    private Decision decisionValues(EntryAiReviewDecisionRequest decision) {
        if (decision == null) {
            throw WorkItemService.bad("An AI review decision is required");
        }
        return new Decision(requiredBody(decision.originalBody()), requiredBody(decision.proposedBody()));
    }

    private CreateDecision createDecisionValues(EntryAiCreateReviewDecisionRequest decision) {
        if (decision == null || WorkItemService.blank(decision.workItemId())) {
            throw WorkItemService.bad("Entry workItemId is required");
        }
        String type = WorkItemService.enumValue(
                decision.type() == null ? "COMMENT" : decision.type(), WorkTypes.ENTRY_TYPES, "Entry type");
        return new CreateDecision(decision.workItemId(), type, requiredBody(decision.originalBody()), requiredBody(decision.proposedBody()));
    }

    private String requiredBody(String body) {
        if (WorkItemService.blank(body)) {
            throw WorkItemService.bad("Entry text is required");
        }
        return body.trim();
    }

    private AuditLogEntry aiAudit(String actorId, String action, Entry entry, Decision values, String decision) {
        Map<String, Object> before = Map.of("body", values.originalBody());
        Map<String, Object> after = Map.of("body", values.proposedBody());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("aiAssisted", true);
        metadata.put("originalInput", values.originalBody());
        metadata.put("llmProposedValue", values.proposedBody());
        metadata.put("decision", decision);
        metadata.put("workItemId", entry.getWorkItemId());
        return new AuditLogEntry(actorId, action, AuditEntityTypes.ENTRY, entry.getId(), entry.getProjectId(), AuditOutcomes.SUCCESS,
                "AI entry revision " + decision.toLowerCase(), auditLogService.json(before), auditLogService.json(after),
                auditLogService.changes(before, after), auditLogService.json(metadata));
    }

    private String reviewInput(String workItemType, String workItemTitle, String entryType, String body, String instruction) {
        return "Work item type: " + workItemType + "\n"
                + "Work item title: " + workItemTitle + "\n"
                + "Current entry type: " + entryType + "\n"
                + "Entry body:\n" + body
                + (WorkItemService.blank(instruction) ? "" : "\n\nAuthor feedback for this revision:\n" + instruction.trim());
    }

    public record Proposal(String proposedBody, String proposedType, String rationale) {
    }

    private record Decision(String originalBody, String proposedBody) {
    }

    private record CreateDecision(String workItemId, String type, String originalBody, String proposedBody) {
    }
}
