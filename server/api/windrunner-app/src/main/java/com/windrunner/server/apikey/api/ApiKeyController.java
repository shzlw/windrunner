package com.windrunner.server.apikey.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyService;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/me/api-keys")
public class ApiKeyController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    @GetMapping
    public ApiResponse<List<ApiKeyResponse>> listApiKeys(@RequestParam(name = "page", defaultValue = "0") int page,
                                                         @RequestParam(name = "size", defaultValue = "25") int size,
                                                         HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = apiKeyService.countOwnedApiKeys(currentUser.getId());
        return ApiResponse.page(
                apiKeyService.listOwnedApiKeys(currentUser.getId(), normalizedSize, (long) normalizedPage * normalizedSize),
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
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
