package com.windrunner.server.audio;

public class AudioTranscriptionException extends RuntimeException {

    public AudioTranscriptionException(String message) {
        super(message);
    }

    public AudioTranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
