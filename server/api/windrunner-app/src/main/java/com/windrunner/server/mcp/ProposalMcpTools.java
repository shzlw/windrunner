package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.WorkspaceChangeProposalPage;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProposalMcpTools {

    private static final int MAX_CHANGES = 25;
    private static final String WORK_ITEM = "WORK_ITEM";
    private static final String ENTRY = "ENTRY";
    private static final String RELATIONSHIP = "RELATIONSHIP";

    private final McpAuthorization authorization;
    private final WorkspaceChangeProposalService proposals;

    @McpTool(
            name = "propose_workspace_changes",
            description = "Create a pending, reviewable proposal for up to 25 work-item, entry, or relationship changes in one project. Keep requestSummary within 500 characters. This tool does not apply the changes. Include at most one change for each existing entity target, and do not submit an UPDATE when the requested values already match the current record. Before proposing an ADD, use the focused list, search, or exact-match tool for that entity; update the exact existing ID when there is a clear match, ask when matches are ambiguous, and add only when no clear match exists. Present the returned before/after preview to the user and do not call decide_proposal with ACCEPT until the user explicitly approves it.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public WorkspaceChangeProposalView proposeWorkspaceChanges(String projectId, String requestSummary,
                                                                ProposalDraft draft) {
        String[] scopes = requireDraftScopes(draft, true);
        AuthenticatedApiKey apiKey = authorization.requireProjectEditorApiKey(projectId, scopes);
        return proposals.createFromMcp(authorization.requireProjectId(projectId), requestSummary,
                apiKey.apiKey().getId(), apiKey.owner(), draft);
    }

    @McpTool(
            name = "list_proposals",
            description = "List a bounded page of workspace proposals created by this exact API key in one project. Optionally filter by PENDING, APPLIED, REJECTED, or COMPLETED. Use this to recover a pending review or find an older applied proposal before requesting a revert.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public WorkspaceChangeProposalPage listProposals(String projectId, String status,
                                                     Integer limit, Long offset) {
        String normalizedProjectId = authorization.requireProjectId(projectId);
        AuthenticatedApiKey apiKey = authorization.requireProjectViewerApiKey(
                normalizedProjectId, ApiKeyScopes.PROJECTS_READ);
        WorkspaceChangeProposalPage page = proposals.listFromMcp(normalizedProjectId,
                apiKey.apiKey().getId(), status, limit, offset);
        return page;
    }

    @McpTool(
            name = "get_proposal",
            description = "Get one workspace proposal created by this exact API key, including its status and complete bounded change preview. Read this immediately before asking the user to accept, reject, or revert the proposal.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public WorkspaceChangeProposalView getProposal(String projectId, String proposalId) {
        String normalizedProjectId = authorization.requireProjectId(projectId);
        AuthenticatedApiKey apiKey = authorization.requireProjectViewerApiKey(
                normalizedProjectId, ApiKeyScopes.PROJECTS_READ);
        authorization.requireProjectViewerApiKey(normalizedProjectId,
                requireEntityScopes(proposals.findMcpEntityTypes(
                        normalizedProjectId, proposalId, apiKey.apiKey().getId()), false));
        return proposals.getFromMcp(normalizedProjectId, proposalId, apiKey.apiKey().getId());
    }

    @McpTool(
            name = "decide_proposal",
            description = "Accept or reject one pending workspace proposal created by this exact API key. ACCEPT applies every open change and can modify or delete application data; call it only after showing the exact get_proposal preview and receiving explicit user approval. REJECT closes the proposal without applying its changes. The server rechecks authorization, proposal state, and target versions before applying.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false,
                    openWorldHint = false))
    public WorkspaceChangeProposalView decideProposal(String projectId, String proposalId, String decision) {
        String normalizedProjectId = authorization.requireProjectId(projectId);
        AuthenticatedApiKey apiKey = authorization.requireProjectViewerApiKey(
                normalizedProjectId, ApiKeyScopes.PROJECTS_READ);
        AuthenticatedApiKey authorizedApiKey = authorization.requireProjectEditorApiKey(
                normalizedProjectId, requireEntityScopes(proposals.findMcpEntityTypes(
                        normalizedProjectId, proposalId, apiKey.apiKey().getId()), true));
        proposals.getFromMcp(normalizedProjectId, proposalId, apiKey.apiKey().getId());
        return proposals.decideAllFromMcp(normalizedProjectId, proposalId,
                new DecisionRequest(decision, null), authorizedApiKey.apiKey().getId(), authorizedApiKey.owner());
    }

    @McpTool(
            name = "create_revert_proposal",
            description = "Create a pending proposal that would reverse an applied workspace proposal created by this exact API key. This tool does not revert data. Show the returned inverse before/after preview to the user and require explicit approval before calling decide_proposal with ACCEPT. Revert creation fails when later changes make a safe reversal impossible.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public WorkspaceChangeProposalView createRevertProposal(String projectId, String proposalId) {
        String normalizedProjectId = authorization.requireProjectId(projectId);
        AuthenticatedApiKey apiKey = authorization.requireProjectViewerApiKey(
                normalizedProjectId, ApiKeyScopes.PROJECTS_READ);
        AuthenticatedApiKey authorizedApiKey = authorization.requireProjectEditorApiKey(
                normalizedProjectId, requireEntityScopes(proposals.findMcpEntityTypes(
                        normalizedProjectId, proposalId, apiKey.apiKey().getId()), true));
        proposals.getFromMcp(normalizedProjectId, proposalId, apiKey.apiKey().getId());
        return proposals.createRevertFromMcp(normalizedProjectId, proposalId,
                authorizedApiKey.apiKey().getId(), authorizedApiKey.owner());
    }

    private String[] requireDraftScopes(ProposalDraft draft, boolean includeWrite) {
        if (draft == null || draft.changes() == null || draft.changes().isEmpty()) {
            throw createBadRequestException("At least one workspace change is required");
        }
        if (draft.changes().size() > MAX_CHANGES) {
            throw createBadRequestException("An MCP proposal can contain at most 25 changes");
        }
        LinkedHashSet<String> scopes = createBaseScopes();
        for (ChangeDraft change : draft.changes()) {
            if (change == null) throw createBadRequestException("Workspace change is required");
            addEntityScopes(scopes, change.entityType(), includeWrite);
        }
        return scopes.toArray(String[]::new);
    }

    private String[] requireEntityScopes(List<String> entityTypes, boolean includeWrite) {
        LinkedHashSet<String> scopes = createBaseScopes();
        for (String entityType : entityTypes) {
            addEntityScopes(scopes, entityType, includeWrite);
        }
        return scopes.toArray(String[]::new);
    }

    private LinkedHashSet<String> createBaseScopes() {
        return new LinkedHashSet<>(Set.of(ApiKeyScopes.PROJECTS_READ));
    }

    private void addEntityScopes(Set<String> scopes, String entityType, boolean includeWrite) {
        String normalizedEntityType = entityType == null ? "" : entityType.trim().toUpperCase();
        switch (normalizedEntityType) {
            case WORK_ITEM -> {
                scopes.add(ApiKeyScopes.WORK_ITEMS_READ);
                if (includeWrite) scopes.add(ApiKeyScopes.WORK_ITEMS_WRITE);
            }
            case ENTRY -> {
                scopes.add(ApiKeyScopes.ENTRIES_READ);
                if (includeWrite) scopes.add(ApiKeyScopes.ENTRIES_WRITE);
            }
            case RELATIONSHIP -> {
                scopes.add(ApiKeyScopes.RELATIONSHIPS_READ);
                if (includeWrite) scopes.add(ApiKeyScopes.RELATIONSHIPS_WRITE);
            }
            default -> throw createBadRequestException(
                    "Entity type must be WORK_ITEM, ENTRY, or RELATIONSHIP");
        }
    }

    private ResponseStatusException createBadRequestException(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
