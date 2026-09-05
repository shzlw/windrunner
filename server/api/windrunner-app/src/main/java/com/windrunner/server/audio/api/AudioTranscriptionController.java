package com.windrunner.server.audio.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audio.AudioTranscriptionAvailabilityService;
import com.windrunner.server.audio.AudioTranscriptionException;
import com.windrunner.server.audio.AudioTranscriptionService;
import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import com.windrunner.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/audio/transcriptions")
public class AudioTranscriptionController {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("flac", "m4a", "mp3", "mp4", "mpeg", "mpga", "ogg", "wav", "webm");
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "audio/flac",
            "audio/mp4",
            "audio/mpeg",
            "audio/ogg",
            "audio/wav",
            "audio/webm",
            "audio/x-flac",
            "audio/x-m4a",
            "audio/x-wav"
    );
    private static final int MAX_LANGUAGE_LENGTH = 16;

    private final ObjectProvider<AudioTranscriptionService> transcriptionServiceProvider;
    private final AudioTranscriptionAvailabilityService availabilityService;
    private final AudioTranscriptionProperties properties;
    private final AuthService authService;

    @GetMapping("/status")
    public ApiResponse<TranscriptionStatus> status() {
        AudioTranscriptionService service = transcriptionServiceProvider.getIfAvailable();
        return ApiResponse.success(new TranscriptionStatus(
                service != null,
                service == null ? availabilityService.provider() : service.provider(),
                service == null ? availabilityService.model() : service.model(),
                properties.getMaxDurationSeconds(),
                properties.getMaxFileSizeBytes()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TranscriptionResponse> transcribe(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "language", required = false) String language,
            HttpServletRequest request
    ) {
        authService.requireCurrentUser(request);
        AudioTranscriptionService service = transcriptionServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Voice transcription is unavailable");
        }

        validate(file, language, service.maxFileSizeBytes());

        try {
            return ApiResponse.success(new TranscriptionResponse(service.transcribe(file, language)));
        } catch (RestClientResponseException exception) {
            log.warn("Audio transcription request failed with status={}, audioBytes={}",
                    exception.getStatusCode().value(), file.getSize());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The transcription service could not complete the request");
        } catch (ResourceAccessException exception) {
            log.warn("Audio transcription request could not reach the service, audioBytes={}", file.getSize());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The transcription service is unavailable");
        } catch (AudioTranscriptionException exception) {
            log.warn("Audio transcription returned no usable text, audioBytes={}", file.getSize());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The transcription service returned an invalid response");
        }
    }

    private void validate(MultipartFile file, String language, long maxFileSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An audio file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The audio file is too large");
        }

        String extension = extension(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The audio format is not supported");
        }

        String contentType = file.getContentType();
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (!normalizedContentType.isBlank() && !SUPPORTED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The audio content type is not supported");
        }

        if (language != null && language.length() > MAX_LANGUAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The language value is invalid");
        }
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot < 0 ? "" : filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    public record TranscriptionResponse(String text) {
    }

    public record TranscriptionStatus(
            boolean available,
            String provider,
            String model,
            int maxDurationSeconds,
            long maxFileSizeBytes
    ) {
    }
}
