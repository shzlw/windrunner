package com.windrunner.server.analytics.api;

import com.windrunner.server.analytics.AiAnalyticsService;
import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/ai-analytics")
public class AiAnalyticsController {

    private final AiAnalyticsService aiAnalyticsService;
    private final AuthService authService;
    private final ProjectRepository projectRepository;

    @GetMapping
    public ApiResponse<AiAnalyticsSummary> summarize(@RequestParam(value = "days", required = false) Integer days,
                                                     HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        if (days != null && days < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be a positive integer");
        OffsetDateTime since = days == null
                ? OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                : OffsetDateTime.now().minusDays(days);
        List<String> projectIds = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findAllByOrderByNameAscIdAsc().stream().map(Project::getId).toList()
                : projectRepository.findVisibleToUser(actor.getId()).stream().map(Project::getId).toList();
        return ApiResponse.success(aiAnalyticsService.summarize(projectIds, since));
    }
}
