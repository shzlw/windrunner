package com.windrunner.server.user.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.UserSettingService;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.domain.SettingValue;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/me/settings")
public class UserSettingController {

    private final AuthService authService;
    private final UserSettingService userSettingService;

    @GetMapping
    public ApiResponse<Map<String, SettingValue>> getSettings(HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        return ApiResponse.success(userSettingService.getAllSettings(currentUser.getId()));
    }

    @PutMapping("/{key}")
    public ApiResponse<SettingValue> updateSetting(@PathVariable("key") String key,
                                                   @RequestBody SettingValue settingValue,
                                                   HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        return ApiResponse.success(userSettingService.updateSetting(currentUser.getId(), key, settingValue));
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> deleteSetting(@PathVariable("key") String key,
                                           HttpServletRequest request) {
        AppUser currentUser = authService.requireCurrentUser(request);
        userSettingService.deleteSetting(currentUser.getId(), key);
        return ApiResponse.success();
    }
}