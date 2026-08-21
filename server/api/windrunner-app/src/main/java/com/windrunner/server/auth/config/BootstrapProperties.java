package com.windrunner.server.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "windrunner.bootstrap")
public class BootstrapProperties {

    private SuperadminProperties superadmin = new SuperadminProperties();

    @Data
    public static class SuperadminProperties {

        private String username;
        private String email;
        private String password;
    }
}
