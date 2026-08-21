package com.windrunner.server.user.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/users")
public class InternalUserController {

    private final UserAdminService userAdminService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<UserResponse>> listUsers(@RequestParam(name = "page", defaultValue = "0") int page,
                                                     @RequestParam(name = "size", defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        UserPageResponse response = userAdminService.listUsers(page, size, currentUser);
        return ApiResponse.page(
                response.items(),
                response.page(),
                response.size(),
                response.totalItems(),
                response.totalPages());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable("id") String id, HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        return ApiResponse.success(userAdminService.getUser(id, currentUser));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@RequestBody CreateUserRequest createRequest, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        return ApiResponse.success(userAdminService.createUser(createRequest, currentUser));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable("id") String id,
                                                @RequestBody UpdateUserRequest updateRequest,
                                                HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        return ApiResponse.success(userAdminService.updateUser(id, updateRequest, currentUser));
    }

    @PostMapping("/{id}/password")
    public ApiResponse<UserResponse> updatePassword(@PathVariable("id") String id,
                                                    @RequestBody UpdateUserPasswordRequest updateRequest,
                                                    HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        return ApiResponse.success(userAdminService.updatePassword(id, updateRequest, currentUser));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable("id") String id, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        userAdminService.deleteUser(id, currentUser);
        return ApiResponse.success();
    }
}
