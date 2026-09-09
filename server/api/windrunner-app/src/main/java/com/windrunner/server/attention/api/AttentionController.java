package com.windrunner.server.attention.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.attention.AttentionService;
import com.windrunner.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/attention")
public class AttentionController {

    private final AttentionService attentionService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<AttentionSummaryView> getSummary(HttpServletRequest request) {
        return ApiResponse.success(attentionService.getSummary(authService.requireCurrentUser(request)));
    }
}
