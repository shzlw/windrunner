package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.CreateUserRequest;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.api.UserPageResponse;
import com.windrunner.server.user.api.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Create and manage users.")
public class ExternalUserController {

    private final UserAdminService userAdminService;
    private final ExternalAccessService externalAccessService;

    @GetMapping
    public ApiResponse<List<UserResponse>> listUsers(@RequestParam(name = "page", defaultValue = "0") int page,
                                                     @RequestParam(name = "size", defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        UserPageResponse response = userAdminService.listUsers(
                page,
                size,
                externalAccessService.requireScope(request, ApiKeyScopes.USERS_READ));
        return ApiResponse.page(
                response.items(),
                response.page(),
                response.size(),
                response.totalItems(),
                response.totalPages());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable("id") String id, HttpServletRequest request) {
        return ApiResponse.success(userAdminService.getUser(
                id,
                externalAccessService.requireScope(request, ApiKeyScopes.USERS_READ)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@RequestBody CreateUserRequest createRequest,
                                                HttpServletRequest request) {
        return ApiResponse.success(userAdminService.createUser(
                createRequest,
                externalAccessService.requireAdminScope(request, ApiKeyScopes.USERS_WRITE)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable("id") String id,
                                                @RequestBody UpdateUserRequest updateRequest,
                                                HttpServletRequest request) {
        return ApiResponse.success(userAdminService.updateUser(
                id,
                updateRequest,
                externalAccessService.requireAdminScope(request, ApiKeyScopes.USERS_WRITE)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable("id") String id, HttpServletRequest request) {
        userAdminService.deleteUser(id, externalAccessService.requireAdminScope(request, ApiKeyScopes.USERS_WRITE));
        return ApiResponse.success();
    }
}
