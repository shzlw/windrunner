package com.windrunner.server.external.v1.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalRelationshipResponse;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.persistence.RelationshipRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExternalRelationshipControllerTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String ACTOR_ID = "user-key-owner";

    @Mock
    private RelationshipService relationships;
    @Mock
    private RelationshipRepository relationshipRepository;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private HttpServletRequest request;

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId(ACTOR_ID);
        return actor;
    }

    private Relationship relationship(String id, String projectId, String type) {
        Relationship relationship = new Relationship();
        relationship.setId(id);
        relationship.setProjectId(projectId);
        relationship.setType(type);
        return relationship;
    }

    @Test
    void listNormalizesTypeFilterAndReturnsPage() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_READ)).thenReturn(actor());
        OffsetDateTime after = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        List<Relationship> items = List.of(relationship("rela-1", PROJECT_ID, "BLOCKED_BY"));
        when(relationshipRepository.findPageByProjectId(PROJECT_ID, "BLOCKED_BY", after, 25, 0L)).thenReturn(items);
        when(relationshipRepository.countByProjectId(PROJECT_ID, "BLOCKED_BY", after)).thenReturn(1L);

        ApiResponse<List<ExternalRelationshipResponse>> response = controller().list(
                PROJECT_ID, 0, 25, " blocked_by ", after, request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.VIEWER);
        assertThat(response.data()).containsExactlyElementsOf(
                items.stream().map(ExternalRelationshipResponse::from).toList());
        assertThat(response.meta().totalItems()).isEqualTo(1L);
    }

    @Test
    void createRequiresEditorAndDelegatesToService() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(actor());
        Relationship body = relationship(null, PROJECT_ID, "BLOCKED_BY");
        Relationship created = relationship("rela-new", PROJECT_ID, "BLOCKED_BY");
        when(relationships.create(PROJECT_ID, body, ACTOR_ID)).thenReturn(created);

        ApiResponse<ExternalRelationshipResponse> response = controller().create(PROJECT_ID, body, request);

        verify(projectAccessService).requireProjectRole(PROJECT_ID, actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(ExternalRelationshipResponse.from(created));
    }

    @Test
    void createRejectsNullBody() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(actor());

        assertThatThrownBy(() -> controller().create(PROJECT_ID, null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(relationships, never()).create(anyString(), any(), anyString());
    }

    @Test
    void updateReasonResolvesProjectFromRelationshipAndRequiresEditor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(actor());
        Relationship current = relationship("rela-1", "project-other", "BLOCKED_BY");
        when(relationships.findInAnyProject("rela-1")).thenReturn(current);
        Relationship reloaded = relationship("rela-1", "project-other", "BLOCKED_BY");
        when(relationships.updateReason("project-other", "rela-1", " why ", ACTOR_ID)).thenReturn(reloaded);

        ApiResponse<ExternalRelationshipResponse> response = controller().updateReason(
                "rela-1", new ExternalRelationshipController.RelationshipReasonRequest(" why "), request);

        verify(projectAccessService).requireProjectRole("project-other", actor(), ProjectRoles.EDITOR);
        assertThat(response.data()).isEqualTo(ExternalRelationshipResponse.from(reloaded));
    }

    @Test
    void updateReasonUnknownRelationshipReturns404WithoutAuthorizationCheck() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(actor());
        when(relationships.findInAnyProject("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Relationship not found"));

        assertThatThrownBy(() -> controller().updateReason("missing",
                new ExternalRelationshipController.RelationshipReasonRequest("why"), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(projectAccessService);
        verify(relationships, never()).updateReason(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void deleteResolvesProjectFromRelationshipAndRequiresEditor() {
        when(externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(actor());
        Relationship current = relationship("rela-1", "project-other", "BLOCKED_BY");
        when(relationships.findInAnyProject("rela-1")).thenReturn(current);

        controller().delete("rela-1", request);

        verify(projectAccessService).requireProjectRole("project-other", actor(), ProjectRoles.EDITOR);
        verify(relationships).delete("project-other", "rela-1", ACTOR_ID);
    }

    private ExternalRelationshipController controller() {
        return new ExternalRelationshipController(
                relationships, relationshipRepository, externalAccessService, projectAccessService);
    }
}
