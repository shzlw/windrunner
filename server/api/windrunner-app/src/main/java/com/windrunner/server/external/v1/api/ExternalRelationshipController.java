package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalRelationshipResponse;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Relationship;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Relationships", description = "Create and manage work item relationships.")
public class ExternalRelationshipController {

    private final RelationshipService relationships;
    private final com.windrunner.server.work.persistence.RelationshipRepository relationshipRepository;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/projects/{projectId}/relationships")
    public ApiResponse<List<ExternalRelationshipResponse>> list(@PathVariable("projectId") String projectId,
                                                @RequestParam(name = "page", defaultValue = "0") int page,
                                                @RequestParam(name = "size", defaultValue = "50") int size,
                                                @RequestParam(name = "type", required = false) String type,
                                                @RequestParam(name = "created_after", required = false)
                                                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                                java.time.OffsetDateTime createdAfter,
                                                HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_READ);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        // Relationship types are stored uppercase; match case-insensitively.
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        List<Relationship> items = relationshipRepository.findPageByProjectId(projectId, normalizedType, createdAfter, normalizedSize, (long) normalizedPage * normalizedSize);
        long totalItems = relationshipRepository.countByProjectId(projectId, normalizedType, createdAfter);
        return ApiResponse.page(
                items.stream().map(ExternalRelationshipResponse::from).toList(),
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    @PostMapping("/projects/{projectId}/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExternalRelationshipResponse> create(@PathVariable("projectId") String projectId,
                                            @RequestBody Relationship relationship,
                                            HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (relationship == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Relationship is required");
        }
        return ApiResponse.success(ExternalRelationshipResponse.from(relationships.create(projectId, relationship, actor.getId())));
    }

    public record RelationshipReasonRequest(String reason) {
    }

    @PutMapping("/relationships/{id}/reason")
    public ApiResponse<ExternalRelationshipResponse> updateReason(@PathVariable("id") String id,
                                                  @RequestBody RelationshipReasonRequest body,
                                                  HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.RELATIONSHIPS_WRITE);
        Relationship current = requireRelationship(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        return ApiResponse.success(ExternalRelationshipResponse.from(relationships.updateReason(
                current.getProjectId(), id, body == null ? null : body.reason(), actor.getId())));
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
