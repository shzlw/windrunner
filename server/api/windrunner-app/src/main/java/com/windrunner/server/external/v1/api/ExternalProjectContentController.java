package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalSearchResultResponse;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.ContentOrderService;
import com.windrunner.server.work.ProjectSearchService;
import com.windrunner.server.work.api.ContentOrderItem;
import com.windrunner.server.work.api.ContentReorderRequest;
import com.windrunner.server.work.api.ProjectSearchResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}")
@Tag(name = "Project content", description = "Search project content and manage content order.")
public class ExternalProjectContentController {

    private final ProjectSearchService searchService;
    private final ContentOrderService contentOrderService;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/search")
    public ApiResponse<ExternalSearchResultResponse> search(@PathVariable("projectId") String projectId,
                                                   @RequestParam(value = "q", defaultValue = "") String query,
                                                   @RequestParam(value = "limit", required = false) Integer limit,
                                                   HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScopes(request,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.ENTRIES_READ,
                ApiKeyScopes.RELATIONSHIPS_READ);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        ExternalInputValidation.requireMaxLength(query, "Search query", ExternalInputValidation.MAX_SEARCH_LENGTH);
        if (query == null || query.isBlank()) {
            return ApiResponse.success(new ExternalSearchResultResponse(List.of(), List.of(), List.of()));
        }
        return ApiResponse.success(ExternalSearchResultResponse.from(searchService.search(projectId, query, limit)));
    }

    @PutMapping("/content-order")
    public ApiResponse<List<ContentOrderItem>> reorder(@PathVariable("projectId") String projectId,
                                                       @RequestBody ContentReorderRequest body,
                                                       HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScopes(request,
                ApiKeyScopes.WORK_ITEMS_WRITE,
                ApiKeyScopes.ENTRIES_WRITE);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (body == null || body.items() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items is required");
        }
        ExternalInputValidation.requireMaxSize(body.items(), "items", ExternalInputValidation.MAX_CONTENT_ORDER_SIZE);
        return ApiResponse.success(contentOrderService.reorder(
                projectId,
                body.parentWorkItemId(),
                body.items()));
    }
}
