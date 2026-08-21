package com.windrunner.server.llm.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.llm.LlmUsageService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/llm-usage")
public class LlmUsageController {

    private final LlmUsageService llmUsageService;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;

    @GetMapping
    public ApiResponse<LlmUsageSummary> summarize(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "days", required = false) Integer days,
            HttpServletRequest request
    ) {
        AppUser actor = authService.requireCurrentUser(request);
        if (days != null && days < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be a positive integer");
        }
        List<String> projectIds = scopedProjectIds(actor, projectId);
        OffsetDateTime since = days == null
                ? OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                : OffsetDateTime.now().minusDays(days);
        return ApiResponse.success(llmUsageService.summarize(projectIds, since));
    }

    private List<String> scopedProjectIds(AppUser actor, String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
            return List.of(projectId);
        }
        List<Project> projects = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findAllByOrderByNameAscIdAsc()
                : projectRepository.findVisibleToUser(actor.getId());
        return projects.stream().map(Project::getId).toList();
    }
}