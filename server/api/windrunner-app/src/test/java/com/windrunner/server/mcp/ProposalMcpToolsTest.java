package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.apikey.domain.ApiKey;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.WorkspaceChangeProposalPage;
import com.windrunner.server.work.api.WorkspaceChangeProposalSummary;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalMcpToolsTest {

    private static final String PROJECT_ID = "project-1";
    private static final String PROPOSAL_ID = "proposal-1";
    private static final String API_KEY_ID = "api-key-1";

    @Mock
    private McpAuthorization authorization;
    @Mock
    private WorkspaceChangeProposalService proposals;

    @Test
    void createsPendingWorkspaceProposalWithExactApiKey() {
        ProposalDraft draft = new ProposalDraft(List.of(new ChangeDraft(
                "WORK_ITEM", "UPDATE", "work-item-1", null, "Update status",
                null, null, null)));
        AuthenticatedApiKey apiKey = authenticatedApiKey();
        WorkspaceChangeProposalView expected = proposalView("WORK_ITEM", "PENDING");
        when(authorization.requireProjectEditorApiKey(
                PROJECT_ID,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(apiKey);
        when(authorization.requireProjectId(PROJECT_ID)).thenReturn(PROJECT_ID);
        when(proposals.createFromMcp(PROJECT_ID, "Update the task", API_KEY_ID,
                apiKey.owner(), draft)).thenReturn(expected);

        WorkspaceChangeProposalView result = tools().proposeWorkspaceChanges(
                PROJECT_ID, "Update the task", draft);

        assertThat(result).isSameAs(expected);
        verify(proposals).createFromMcp(PROJECT_ID, "Update the task", API_KEY_ID,
                apiKey.owner(), draft);
    }

    @Test
    void listsOnlyProposalsForTheAuthenticatedApiKey() {
        AuthenticatedApiKey apiKey = authenticatedApiKey();
        WorkspaceChangeProposalSummary proposal = new WorkspaceChangeProposalSummary(
                PROPOSAL_ID, PROJECT_ID, "Update entry", null, "PENDING", null, null);
        WorkspaceChangeProposalPage expected = new WorkspaceChangeProposalPage(
                List.of(proposal), 1, 1, 20, 0, false);
        when(authorization.requireProjectId(PROJECT_ID)).thenReturn(PROJECT_ID);
        when(authorization.requireProjectViewerApiKey(PROJECT_ID, ApiKeyScopes.PROJECTS_READ))
                .thenReturn(apiKey);
        when(proposals.listFromMcp(PROJECT_ID, API_KEY_ID, "PENDING", 20, 0L))
                .thenReturn(expected);

        WorkspaceChangeProposalPage result = tools().listProposals(
                PROJECT_ID, "PENDING", 20, 0L);

        assertThat(result).isSameAs(expected);
        verify(proposals).listFromMcp(PROJECT_ID, API_KEY_ID, "PENDING", 20, 0L);
    }

    @Test
    void loadsProposalContentsOnlyAfterCheckingItsEntityScopes() {
        AuthenticatedApiKey apiKey = authenticatedApiKey();
        WorkspaceChangeProposalView expected = proposalView("ENTRY", "PENDING");
        when(authorization.requireProjectId(PROJECT_ID)).thenReturn(PROJECT_ID);
        when(authorization.requireProjectViewerApiKey(PROJECT_ID, ApiKeyScopes.PROJECTS_READ))
                .thenReturn(apiKey);
        when(proposals.findMcpEntityTypes(PROJECT_ID, PROPOSAL_ID, API_KEY_ID))
                .thenReturn(List.of("ENTRY"));
        when(authorization.requireProjectViewerApiKey(
                PROJECT_ID,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.ENTRIES_READ)).thenReturn(apiKey);
        when(proposals.getFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID)).thenReturn(expected);

        WorkspaceChangeProposalView result = tools().getProposal(PROJECT_ID, PROPOSAL_ID);

        assertThat(result).isSameAs(expected);
        verify(proposals).findMcpEntityTypes(PROJECT_ID, PROPOSAL_ID, API_KEY_ID);
        verify(proposals).getFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID);
    }

    @Test
    void acceptsProposalOnlyAfterLoadingItsExactPreview() {
        AuthenticatedApiKey apiKey = authenticatedApiKey();
        WorkspaceChangeProposalView preview = proposalView("RELATIONSHIP", "PENDING");
        WorkspaceChangeProposalView applied = proposalView("RELATIONSHIP", "APPLIED");
        when(authorization.requireProjectId(PROJECT_ID)).thenReturn(PROJECT_ID);
        when(authorization.requireProjectViewerApiKey(PROJECT_ID, ApiKeyScopes.PROJECTS_READ))
                .thenReturn(apiKey);
        when(proposals.findMcpEntityTypes(PROJECT_ID, PROPOSAL_ID, API_KEY_ID))
                .thenReturn(List.of("RELATIONSHIP"));
        when(authorization.requireProjectEditorApiKey(
                PROJECT_ID,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.RELATIONSHIPS_READ,
                ApiKeyScopes.RELATIONSHIPS_WRITE)).thenReturn(apiKey);
        when(proposals.getFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID)).thenReturn(preview);
        when(proposals.decideAllFromMcp(PROJECT_ID, PROPOSAL_ID,
                new DecisionRequest("ACCEPT", null), API_KEY_ID, apiKey.owner())).thenReturn(applied);

        WorkspaceChangeProposalView result = tools().decideProposal(
                PROJECT_ID, PROPOSAL_ID, "ACCEPT");

        assertThat(result).isSameAs(applied);
        verify(proposals).getFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID);
        verify(proposals).decideAllFromMcp(PROJECT_ID, PROPOSAL_ID,
                new DecisionRequest("ACCEPT", null), API_KEY_ID, apiKey.owner());
    }

    @Test
    void createsPendingRevertWithoutApplyingIt() {
        AuthenticatedApiKey apiKey = authenticatedApiKey();
        WorkspaceChangeProposalView original = proposalView("WORK_ITEM", "APPLIED");
        WorkspaceChangeProposalView revert = proposalView("WORK_ITEM", "PENDING");
        when(authorization.requireProjectId(PROJECT_ID)).thenReturn(PROJECT_ID);
        when(authorization.requireProjectViewerApiKey(PROJECT_ID, ApiKeyScopes.PROJECTS_READ))
                .thenReturn(apiKey);
        when(proposals.findMcpEntityTypes(PROJECT_ID, PROPOSAL_ID, API_KEY_ID))
                .thenReturn(List.of("WORK_ITEM"));
        when(authorization.requireProjectEditorApiKey(
                PROJECT_ID,
                ApiKeyScopes.PROJECTS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.WORK_ITEMS_WRITE)).thenReturn(apiKey);
        when(proposals.getFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID)).thenReturn(original);
        when(proposals.createRevertFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID, apiKey.owner()))
                .thenReturn(revert);

        WorkspaceChangeProposalView result = tools().createRevertProposal(PROJECT_ID, PROPOSAL_ID);

        assertThat(result).isSameAs(revert);
        verify(proposals).createRevertFromMcp(PROJECT_ID, PROPOSAL_ID, API_KEY_ID, apiKey.owner());
        verify(proposals, never()).decideAllFromMcp(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsOversizedMcpProposalBeforeAuthorization() {
        ChangeDraft change = new ChangeDraft(
                "WORK_ITEM", "ADD", null, "new-item", "Create item",
                null, null, null);
        ProposalDraft draft = new ProposalDraft(Collections.nCopies(26, change));

        assertThatThrownBy(() -> tools().proposeWorkspaceChanges(PROJECT_ID, null, draft))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(authorization, never()).requireProjectEditorApiKey(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<String[]>any());
    }

    private ProposalMcpTools tools() {
        return new ProposalMcpTools(authorization, proposals);
    }

    private AuthenticatedApiKey authenticatedApiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(API_KEY_ID);
        AppUser owner = new AppUser();
        owner.setId("user-1");
        return new AuthenticatedApiKey(apiKey, owner, List.of());
    }

    private WorkspaceChangeProposalView proposalView(String entityType, String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-13T12:00:00Z");
        WorkspaceChangeProposalView.ChangeView change = new WorkspaceChangeProposalView.ChangeView(
                "change-1", 0, entityType, "UPDATE", "target-1", "Update target",
                status, null, null, now, now,
                null, null, null, null, null, null);
        return new WorkspaceChangeProposalView(
                PROPOSAL_ID, PROJECT_ID, null, null, "Update target", null,
                status, now, now, List.of(change));
    }
}
