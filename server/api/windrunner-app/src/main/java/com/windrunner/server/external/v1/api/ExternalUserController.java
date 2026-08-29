package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalUserIdentityResponse;
import com.windrunner.server.user.UserStatuses;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Resolve limited user identity information.")
public class ExternalUserController {

    private final AppUserRepository appUserRepository;
    private final ExternalAccessService externalAccessService;

    @GetMapping("/{id}")
    public ApiResponse<ExternalUserIdentityResponse> getUser(@PathVariable("id") String id,
                                                              HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.USERS_READ);
        AppUser user = appUserRepository.findById(id)
                .filter(candidate -> UserStatuses.ACTIVE.equalsIgnoreCase(candidate.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ApiResponse.success(ExternalUserIdentityResponse.from(user));
    }
}
