package com.windrunner.server.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "windrunner.llm")
public class LlmProperties {

    private String provider = "none";

    private Duration agentTimeout;
}
