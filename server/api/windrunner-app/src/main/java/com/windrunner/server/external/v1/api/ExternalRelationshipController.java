package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Relationship;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExternalRelationshipController {

    private final RelationshipService relationships;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/projects/{projectId}/relationships")
    public ApiResponse<List<Relationship>> list(@PathVariable("projectId") String projectId,
                                                HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_READ);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        return ApiResponse.success(relationships.list(projectId));
    }

    @PostMapping("/projects/{projectId}/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Relationship> create(@PathVariable("projectId") String projectId,
                                            @RequestBody Relationship relationship,
                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (relationship == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Relationship is required");
        }
        return ApiResponse.success(relationships.create(projectId, relationship, actor.getId()));
    }

    public record RelationshipReasonRequest(String reason) {
    }

    @PutMapping("/relationships/{id}/reason")
    public ApiResponse<Relationship> updateReason(@PathVariable("id") String id,
                                                  @RequestBody RelationshipReasonRequest body,
                                                  HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE);
        Relationship current = requireRelationship(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        return ApiResponse.success(relationships.updateReason(
                current.getProjectId(), id, body == null ? null : body.reason(), actor.getId()));
    }

    @DeleteMapping("/relationships/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id,
                                    HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE);
        Relationship current = requireRelationship(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        relationships.delete(current.getProjectId(), id, actor.getId());
        return ApiResponse.success();
    }

    private Relationship requireRelationship(String id) {
        return relationships.findInAnyProject(id);
    }
}
