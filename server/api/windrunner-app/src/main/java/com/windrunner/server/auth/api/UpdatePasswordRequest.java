package com.windrunner.server.auth.api;

import lombok.Data;

@Data
public class UpdatePasswordRequest {

    private String newPassword;

    private String currentPassword;
}
