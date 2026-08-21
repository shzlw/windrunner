package com.windrunner.server.auth.api;

import lombok.Data;

@Data
public class LoginRequest {

    private String login;

    private String password;
}
