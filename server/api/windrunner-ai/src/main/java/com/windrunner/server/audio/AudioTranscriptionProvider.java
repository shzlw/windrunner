package com.windrunner.server.audio;

public interface AudioTranscriptionProvider {

    String id();

    String model();

    String transcribe(AudioTranscriptionRequest request);
}
