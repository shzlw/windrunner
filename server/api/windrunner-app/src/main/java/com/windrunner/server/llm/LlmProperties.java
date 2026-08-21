package com.windrunner.server.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "windrunner.llm")
public class LlmProperties {

    private String provider = "none";
}
