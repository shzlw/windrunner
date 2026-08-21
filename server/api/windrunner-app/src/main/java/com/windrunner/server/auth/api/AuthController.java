package com.windrunner.server.auth.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.login(request, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.success();
    }

    @PostMapping("/password")
    public ApiResponse<AuthUserResponse> updatePassword(@RequestBody UpdatePasswordRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        return ApiResponse.success(authService.updatePassword(request, httpRequest, httpResponse));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(HttpServletRequest request) {
        return ApiResponse.success(authService.getCurrentUserResponse(request));
    }
}
