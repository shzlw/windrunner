package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.api.WorkItemPayload;
import com.windrunner.server.work.domain.WorkItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkItemProposalRevertTest {

    @Test
    void restoresOnlyOriginalFieldsAndPreservesLaterUnrelatedChanges() {
        WorkItemService workItemService = mock(WorkItemService.class);
        WorkItem before = workItem("Old title", "LOW");
        WorkItem after = workItem("New title", "LOW");
        WorkItem current = workItem("New title", "HIGH");
        when(workItemService.get("project-1", "work-1")).thenReturn(current);
        when(workItemService.findAssignees("work-1")).thenReturn(List.of());

        WorkspaceProposalChange revert = new WorkItemProposalHandler(
                workItemService, mock(ProjectAccessService.class)).buildRevert(
                storedChange("UPDATE", before, after), new AppUser());

        assertThat(revert.draft().workItem().title()).isEqualTo("Old title");
        assertThat(revert.draft().workItem().priority()).isNull();
    }

    @Test
    void rejectsRevertWhenTheSameFieldChangedAgain() {
        WorkItemService workItemService = mock(WorkItemService.class);
        when(workItemService.get("project-1", "work-1")).thenReturn(workItem("Newest title", "LOW"));
        when(workItemService.findAssignees("work-1")).thenReturn(List.of());

        assertThatThrownBy(() -> new WorkItemProposalHandler(
                workItemService, mock(ProjectAccessService.class)).buildRevert(
                storedChange("UPDATE", workItem("Old title", "LOW"), workItem("New title", "LOW")),
                new AppUser()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsWorkItemCreationRevert() {
        assertThatThrownBy(() -> new WorkItemProposalHandler(
                mock(WorkItemService.class), mock(ProjectAccessService.class)).buildRevert(
                storedChange("ADD", workItem("New title", "LOW"), workItem("New title", "LOW")),
                new AppUser()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private ProposalChange storedChange(String operation, WorkItem before, WorkItem after) {
        ProposalChange change = new ProposalChange();
        change.setOperation(operation);
        change.setTargetRef(JsonUtils.toJson(new WorkspaceProposalTarget(
                "project-1", "work-1", "change work item")));
        change.setBeforeSnapshot(JsonUtils.toJson(new WorkItemPayload(before, List.of())));
        change.setAfterSnapshot(JsonUtils.toJson(new WorkItemPayload(after, List.of())));
        return change;
    }

    private WorkItem workItem(String title, String priority) {
        WorkItem item = new WorkItem();
        item.setId("work-1");
        item.setProjectId("project-1");
        item.setTitle(title);
        item.setType("TASK");
        item.setStatus("OPEN");
        item.setPriority(priority);
        return item;
    }
}
