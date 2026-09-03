package com.windrunner.server.system.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audio.AudioTranscriptionService;
import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.llm.LlmAvailabilityService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/system-information")
public class SystemInformationController {

    private final AuthService authService;
    private final LlmAvailabilityService llmAvailabilityService;
    private final AudioTranscriptionProperties audioTranscriptionProperties;
    private final ObjectProvider<AudioTranscriptionService> audioTranscriptionService;
    private final ObjectProvider<BuildProperties> buildProperties;

    @GetMapping
    public ApiResponse<SystemInformation> getSystemInformation(HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);

        BuildProperties build = buildProperties.getIfAvailable();
        String version = build == null || build.getVersion() == null || build.getVersion().isBlank()
                ? "—"
                : build.getVersion();
        AudioTranscriptionService audioService = audioTranscriptionService.getIfAvailable();
        return ApiResponse.success(new SystemInformation(
                version,
                llmAvailabilityService.provider(),
                llmAvailabilityService.model(),
                llmAvailabilityService.available(),
                audioService == null
                        ? audioTranscriptionProperties.configuredProvider()
                        : audioService.provider(),
                audioService == null
                        ? audioTranscriptionProperties.configuredModel()
                        : audioService.model(),
                audioService != null));
    }

    public record SystemInformation(
            String serverVersion,
            String llmProvider,
            String llmModel,
            boolean llmAvailable,
            String audioTranscriptionProvider,
            String audioTranscriptionModel,
            boolean audioTranscriptionAvailable
    ) {
    }
}
