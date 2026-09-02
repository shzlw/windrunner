package com.windrunner.server.tools.work;

import com.windrunner.server.llm.LlmTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

@Component
public class ProposeEntryRevisionTool {
    public LlmTool<Parameters> forReview(Consumer<Parameters> onProposal) {
        return new LlmTool<>(
                "propose_entry_revision",
                "Submit the reviewed entry body, proposed entry type, and a concise rationale for the revision.",
                Parameters.class,
                proposal -> {
                    onProposal.accept(proposal);
                    return Map.of("recorded", true);
                });
    }

    public record Parameters(String proposedBody, String proposedType, String rationale) { }
}
