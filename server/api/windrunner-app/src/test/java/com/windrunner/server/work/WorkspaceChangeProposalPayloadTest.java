package com.windrunner.server.work;

import static org.assertj.core.api.Assertions.assertThat;

import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.llmproviders.openai.client.OpenAIJsonSchema;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WorkspaceChangeProposalPayloadTest {

    @Test
    void roundTripsACompleteWorkItemProposalPayload() {
        WorkItem item = new WorkItem();
        item.setId("wi_proposed");
        item.setProjectId("proj_test");
        item.setParentWorkItemId("wi_parent");
        item.setType("TASK");
        item.setTitle("Prepare launch notes");
        item.setStatus("IN_PROGRESS");
        item.setDueDate(LocalDate.of(2026, 8, 21));
        item.setPriority("HIGH");
        WorkItemAssignee assignee = new WorkItemAssignee();
        assignee.setAssigneeType("TEAM");
        assignee.setAssigneeId("team_marketing");
        var payload = new WorkspaceChangeProposalService.WorkItemPayload(item, List.of(assignee));

        var restored = JsonUtils.fromJson(JsonUtils.toJson(payload), WorkspaceChangeProposalService.WorkItemPayload.class);

        assertThat(restored.workItem().getTitle()).isEqualTo("Prepare launch notes");
        assertThat(restored.workItem().getDueDate()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(restored.assignees()).singleElement().satisfies(value -> {
            assertThat(value.getAssigneeType()).isEqualTo("TEAM");
            assertThat(value.getAssigneeId()).isEqualTo("team_marketing");
        });
    }

    @Test
    void generatesTheWorkspaceProposalToolSchema() {
        var schema = new OpenAIJsonSchema(new ObjectMapper()).generate(WorkspaceChangeProposalService.ProposalDraft.class);

        assertThat(schema.path("properties").path("changes").isArray()).isFalse();
        assertThat(schema.path("properties").has("changes")).isTrue();
    }
}
