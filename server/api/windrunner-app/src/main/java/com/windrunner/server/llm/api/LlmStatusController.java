package com.windrunner.server.llm.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.llm.LlmAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/llm")
public class LlmStatusController {

    private final LlmAvailabilityService availabilityService;

    @GetMapping("/status")
    public ApiResponse<LlmStatus> status() {
        return ApiResponse.success(new LlmStatus(availabilityService.provider(), availabilityService.available()));
    }

    public record LlmStatus(String provider, boolean available) {
    }
}
