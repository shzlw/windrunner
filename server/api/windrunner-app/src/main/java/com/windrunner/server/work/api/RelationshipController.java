package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Relationship;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/projects/{projectId}/relationships")
public class RelationshipController {
    private final RelationshipService service;
    private final AuthService auth;
    private final ProjectAccessService access;

    @GetMapping
    public ApiResponse<List<Relationship>> list(@PathVariable("projectId") String projectId, jakarta.servlet.http.HttpServletRequest request) {
        access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER);
        return ApiResponse.success(service.list(projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Relationship> create(@PathVariable("projectId") String projectId, @RequestBody Relationship relationship, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return ApiResponse.success(service.create(projectId, relationship, actor.getId()));
    }

    @PutMapping("/{id}/reason")
    public ApiResponse<Relationship> updateReason(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody RelationshipReasonRequest body, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        return ApiResponse.success(service.updateReason(projectId, id, body == null ? null : body.reason(), actor.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("projectId") String projectId, @PathVariable("id") String id, jakarta.servlet.http.HttpServletRequest request) {
        AppUser actor = auth.requireCurrentUser(request);
        access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        service.delete(projectId, id, actor.getId());
        return ApiResponse.success();
    }

    public record RelationshipReasonRequest(String reason) {
    }
}
