package com.windrunner.server.apikey.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyService;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/me/api-keys")
public class ApiKeyController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    @GetMapping
    public ApiResponse<List<ApiKeyResponse>> listApiKeys(HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        return ApiResponse.success(apiKeyService.listOwnedApiKeys(currentUser.getId()));
    }

    @PostMapping
    public ApiResponse<CreatedApiKeyResponse> createApiKey(@RequestBody CreateApiKeyRequest createRequest,
                                                           HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        return ApiResponse.success(apiKeyService.createApiKey(currentUser, createRequest));
    }

    @DeleteMapping("/{apiKeyId}")
    public ApiResponse<Void> revokeApiKey(@PathVariable("apiKeyId") String apiKeyId, HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        apiKeyService.revokeOwnedApiKey(currentUser, apiKeyId);
        return ApiResponse.success();
    }
}
