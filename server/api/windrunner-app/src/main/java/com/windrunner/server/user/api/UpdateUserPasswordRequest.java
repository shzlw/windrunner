package com.windrunner.server.user.api;

import lombok.Data;

@Data
public class UpdateUserPasswordRequest {

    private String newPassword;

    private Boolean mustChangePassword;
}
