package com.windrunner.server.audio;

import com.windrunner.server.audio.config.AudioTranscriptionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiTranscriptionProviderTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GeminiTranscriptionProvider provider;

    @BeforeEach
    void setUp() {
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();
        properties.getGemini().setApiKey("test-key");
        properties.getGemini().setBaseUrl("https://api.test/v1beta");
        properties.getGemini().setModel("gemini-2.5-flash");

        restClientBuilder = RestClient.builder()
                .baseUrl(properties.getGemini().getBaseUrl())
                .defaultHeader("x-goog-api-key", properties.getGemini().getApiKey());
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new GeminiTranscriptionProvider(restClientBuilder.build(), properties.getGemini());
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void sendsInlineAudioToGenerateContentAndReturnsText() {
        server.expect(requestTo("https://api.test/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "contents": [{
                            "parts": [
                              {"text": "Transcribe the spoken words in this audio. Return only the transcription without commentary."},
                              {"inline_data": {"mime_type": "audio/webm", "data": "YXVkaW8tYnl0ZXM="}}
                            ]
                          }]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "candidates": [{
                            "content": {
                              "parts": [{"text": "  Hello from Gemini.  "}]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.webm",
                "audio/webm;codecs=opus",
                "audio-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(provider.id()).isEqualTo("gemini");
        assertThat(provider.transcribe(AudioTranscriptionRequest.from(file, "en")))
                .isEqualTo("Hello from Gemini.");
    }
}
