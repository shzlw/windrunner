package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.RelationshipDraft;
import com.windrunner.server.work.domain.Relationship;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
}
