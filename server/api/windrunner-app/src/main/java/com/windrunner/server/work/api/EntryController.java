package com.windrunner.server.work.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.EntryAiReviewService;
import com.windrunner.server.work.domain.Entry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/internal-api/v1/projects/{projectId}/entries")
public class EntryController {
    private final EntryService service; private final EntryAiReviewService aiReviewService; private final AuthService auth; private final ProjectAccessService access;
    @GetMapping public ApiResponse<List<Entry>> list(@PathVariable("projectId") String projectId, jakarta.servlet.http.HttpServletRequest request) { access.requireProjectRole(projectId, auth.requireCurrentUser(request), ProjectRoles.VIEWER); return ApiResponse.success(service.list(projectId)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Entry> create(@PathVariable("projectId") String projectId, @RequestBody Entry entry, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(service.create(projectId, entry, actor.getId())); }
    @PostMapping("/ai-review") public ApiResponse<EntryAiReviewResponse> reviewNewWithAi(@PathVariable("projectId") String projectId, @RequestBody EntryAiNewReviewRequest review, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(aiReviewService.reviewNew(projectId, review, actor.getId())); }
    @PostMapping("/ai-review/accept") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Entry> acceptNewAiReview(@PathVariable("projectId") String projectId, @RequestBody EntryAiCreateReviewDecisionRequest decision, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(aiReviewService.acceptNew(projectId, decision, actor.getId())); }
    @PostMapping("/ai-review/reject") public ApiResponse<Void> rejectNewAiReview(@PathVariable("projectId") String projectId, @RequestBody EntryAiCreateReviewDecisionRequest decision, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); aiReviewService.rejectNew(projectId, decision, actor.getId()); return ApiResponse.success(); }
    @PutMapping("/{id}") public ApiResponse<Entry> update(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody Entry entry, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(service.update(projectId, id, entry, actor.getId())); }
    @PostMapping("/{id}/ai-review") public ApiResponse<EntryAiReviewResponse> reviewWithAi(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody EntryAiReviewRequest review, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(aiReviewService.review(projectId, id, review.body(), review.type(), review.instruction(), actor.getId())); }
    @PostMapping("/{id}/ai-review/accept") public ApiResponse<Entry> acceptAiReview(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody EntryAiReviewDecisionRequest decision, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); return ApiResponse.success(aiReviewService.accept(projectId, id, decision, actor.getId())); }
    @PostMapping("/{id}/ai-review/reject") public ApiResponse<Void> rejectAiReview(@PathVariable("projectId") String projectId, @PathVariable("id") String id, @RequestBody EntryAiReviewDecisionRequest decision, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); aiReviewService.reject(projectId, id, decision, actor.getId()); return ApiResponse.success(); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable("projectId") String projectId, @PathVariable("id") String id, jakarta.servlet.http.HttpServletRequest request) { AppUser actor = auth.requireCurrentUser(request); access.requireProjectRole(projectId, actor, ProjectRoles.EDITOR); service.delete(projectId, id, actor.getId()); return ApiResponse.success(); }
}
