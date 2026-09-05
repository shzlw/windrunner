package com.windrunner.server.audio;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

public record AudioTranscriptionRequest(
        Resource audio,
        String filename,
        MediaType mediaType,
        String language,
        long audioBytes
) {

    public static AudioTranscriptionRequest from(MultipartFile file, String language) {
        return new AudioTranscriptionRequest(
                file.getResource(),
                safeFilename(file.getOriginalFilename()),
                mediaType(file),
                StringUtils.hasText(language) ? language.trim() : null,
                file.getSize());
    }

    private static MediaType mediaType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (IllegalArgumentException ignored) {
                // Fall back to a generic media type; the extension-bearing filename remains present.
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static String safeFilename(String filename) {
        String normalized = Objects.toString(filename, "voice-message.webm").replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String basename = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return StringUtils.hasText(basename) ? basename : "voice-message.webm";
    }
}
