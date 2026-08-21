package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects/{projectId}/graph-change-proposals")
public class WorkspaceChangeProposalController {
    private final WorkspaceChangeProposalService service;
    private final AuthService auth;
    private final ProjectAccessService access;

    @GetMapping
    public ApiResponse<List<WorkspaceChangeProposalView>> list(@PathVariable("projectId") String projectId,
                                                               jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        return ApiResponse.success(service.list(projectId));
    }

    @PostMapping("/{proposalId}/changes/{changeId}/decision")
    public ApiResponse<WorkspaceChangeProposalView> decide(@PathVariable("projectId") String projectId,
                                                           @PathVariable("proposalId") String proposalId,
                                                           @PathVariable("changeId") String changeId,
                                                           @RequestBody WorkspaceChangeProposalService.DecisionRequest body,
                                                           jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return ApiResponse.success(service.decide(projectId, proposalId, changeId, body, actor.getId()));
    }
}
