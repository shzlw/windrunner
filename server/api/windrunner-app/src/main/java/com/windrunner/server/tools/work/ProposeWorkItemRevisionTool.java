package com.windrunner.server.tools.work;

import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.work.domain.WorkItemAssignee;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class ProposeWorkItemRevisionTool {
    public LlmTool<Parameters> forReview(Consumer<Parameters> onProposal) {
        return new LlmTool<>(
                "propose_work_item_revision",
                "Submit a conservative WorkItem revision.",
                Parameters.class,
                proposal -> {
                    onProposal.accept(proposal);
                    return Map.of("recorded", true);
                });
    }

    public record Parameters(String proposedTitle, String proposedType, String proposedStatus, String proposedDueDate,
                             String proposedPriority, List<WorkItemAssignee> proposedAssignees,
                             List<ProposedBlocker> proposedBlockers, String rationale) { }

    public record ProposedBlocker(String workItemId, String reason) { }
}
