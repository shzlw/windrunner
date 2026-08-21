package com.windrunner.server.utils;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class FileUtils {

    private static final String SYSTEM_PROMPTS_PATH = "system-prompts/";
    private static final Map<String, String> SYSTEM_PROMPT_CACHE = new ConcurrentHashMap<>();

    public String loadResourceAsString(String resourcePath) {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        try {
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource: " + resourcePath, e);
        }
    }

    public String loadSystemPrompt(String promptName) {
        return SYSTEM_PROMPT_CACHE.computeIfAbsent(
                promptName,
                key -> loadResourceAsString(SYSTEM_PROMPTS_PATH + key)
        );
    }
}
