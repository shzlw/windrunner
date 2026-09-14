package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.RelationshipDraft;
import com.windrunner.server.work.domain.Relationship;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationshipProposalHandlerTest {

    @Test
    void updateWithNullReasonPreservesExistingReason() {
        Relationship proposed = prepareUpdate(null);

        assertThat(proposed.getReason()).isEqualTo("Existing reason");
    }

    @Test
    void updateWithBlankReasonClearsExistingReason() {
        Relationship proposed = prepareUpdate("  ");

        assertThat(proposed.getReason()).isNull();
    }

    @Test
    void restoresADeletedRelationshipWhenNoReplacementExists() {
        RelationshipService relationshipService = mock(RelationshipService.class);
        when(relationshipService.list("project-1")).thenReturn(List.of());
        Relationship previous = relationship("relationship-1", "Existing reason");
        ProposalChange stored = storedChange("DELETE", previous, previous);

        WorkspaceProposalChange revert = new RelationshipProposalHandler(
                relationshipService, mock(ProjectAccessService.class)).buildRevert(stored, new AppUser());

        assertThat(revert.action()).isEqualTo("ADD");
        assertThat(revert.draft().relationship().reason()).isEqualTo("Existing reason");
    }

    @Test
    void rejectsDeletedRelationshipRevertWhenEquivalentRelationshipExists() {
        RelationshipService relationshipService = mock(RelationshipService.class);
        Relationship previous = relationship("relationship-1", "Existing reason");
        when(relationshipService.list("project-1")).thenReturn(List.of(relationship("relationship-2", "Other reason")));
        RelationshipProposalHandler handler = new RelationshipProposalHandler(
                relationshipService, mock(ProjectAccessService.class));

        assertThatThrownBy(() -> handler.buildRevert(storedChange("DELETE", previous, previous), new AppUser()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private Relationship prepareUpdate(String requestedReason) {
        RelationshipService relationshipService = mock(RelationshipService.class);
        Relationship current = new Relationship();
        current.setId("relationship-1");
        current.setProjectId("project-1");
        current.setFromEntityType("WORK_ITEM");
        current.setFromEntityId("work-item-1");
        current.setToEntityType("WORK_ITEM");
        current.setToEntityId("work-item-2");
        current.setType("BLOCKS");
        current.setReason("Existing reason");
        when(relationshipService.list("project-1")).thenReturn(List.of(current));

        RelationshipProposalHandler handler = new RelationshipProposalHandler(
                relationshipService, mock(ProjectAccessService.class));
        RelationshipDraft relationshipDraft = new RelationshipDraft(
                null, null, null, null, null, requestedReason, null);
        ChangeDraft draft = new ChangeDraft(
                "RELATIONSHIP", "UPDATE", "relationship-1", null, "Update relationship",
                null, null, relationshipDraft);
        WorkspaceProposalChange change = new WorkspaceProposalChange(
                "project-1", "UPDATE", "relationship-1", "Update relationship", draft, Map.of());

        WorkspacePreparedChange prepared = handler.prepare(change, new AppUser());
        return JsonUtils.fromJson(prepared.payloadJson(), Relationship.class);
    }

    private ProposalChange storedChange(String operation, Relationship before, Relationship after) {
        ProposalChange change = new ProposalChange();
        change.setOperation(operation);
        change.setTargetRef(JsonUtils.toJson(new WorkspaceProposalTarget(
                "project-1", "relationship-1", "change relationship")));
        change.setBeforeSnapshot(JsonUtils.toJson(before));
        change.setAfterSnapshot(JsonUtils.toJson(after));
        return change;
    }

    private Relationship relationship(String id, String reason) {
        Relationship relationship = new Relationship();
        relationship.setId(id);
        relationship.setProjectId("project-1");
        relationship.setFromEntityType("WORK_ITEM");
        relationship.setFromEntityId("work-item-1");
        relationship.setToEntityType("WORK_ITEM");
        relationship.setToEntityId("work-item-2");
        relationship.setType("BLOCKS");
        relationship.setReason(reason);
        return relationship;
    }
}
