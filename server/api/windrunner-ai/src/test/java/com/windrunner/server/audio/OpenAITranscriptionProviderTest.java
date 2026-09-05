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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAITranscriptionProviderTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private OpenAITranscriptionProvider provider;

    @BeforeEach
    void setUp() {
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setBaseUrl("https://api.test/v1");
        properties.getOpenai().setModel("gpt-transcribe");

        restClientBuilder = RestClient.builder()
                .baseUrl(properties.getOpenai().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getOpenai().getApiKey());
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new OpenAITranscriptionProvider(restClientBuilder.build(), properties.getOpenai());
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void sendsAudioAsMultipartAndReturnsText() {
        server.expect(requestTo("https://api.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)))
                .andRespond(withSuccess("{\"text\":\"  Hello from voice.  \"}", MediaType.APPLICATION_JSON));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.webm",
                "audio/webm",
                "audio-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(provider.transcribe(AudioTranscriptionRequest.from(file, "en")))
                .isEqualTo("Hello from voice.");
    }
}
