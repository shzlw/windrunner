package com.windrunner.server.user.api;

import lombok.Data;

@Data
public class CreateUserRequest {

    private String username;

    private String email;

    private String displayName;

    private String timezone;

    private String password;

    private String status;

    private String globalRole;
}
