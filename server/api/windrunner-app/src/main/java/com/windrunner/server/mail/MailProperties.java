package com.windrunner.server.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "windrunner.mail")
public class MailProperties {

    private boolean enabled = false;
    private String from;
    private String baseUrl;
}
