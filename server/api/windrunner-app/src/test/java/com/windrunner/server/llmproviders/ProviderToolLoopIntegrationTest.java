package com.windrunner.server.llmproviders;

import com.windrunner.server.llm.AgentService;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmProperties;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.llm.config.LlmExecutionConfig;
import com.windrunner.server.llmproviders.claude.ClaudeService;
import com.windrunner.server.llmproviders.claude.config.ClaudeProperties;
import com.windrunner.server.llmproviders.gemini.GeminiService;
import com.windrunner.server.llmproviders.gemini.config.GeminiProperties;
import com.windrunner.server.llmproviders.openai.OpenAIService;
import com.windrunner.server.llmproviders.openai.config.OpenAIProperties;
import com.windrunner.server.llmproviders.openrouter.OpenRouterService;
import com.windrunner.server.llmproviders.openrouter.config.OpenRouterProperties;
import com.windrunner.server.llmproviders.ollama.OllamaService;
import com.windrunner.server.llmproviders.ollama.config.OllamaProperties;
import com.windrunner.server.llmproviders.groq.GroqService;
import com.windrunner.server.llmproviders.groq.config.GroqProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderToolLoopIntegrationTest {

    private static final String BASE_URL = "https://llm.test/v1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExecutorService agentExecutor;
    private ExecutorService toolExecutor;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        LlmExecutionConfig executionConfig = new LlmExecutionConfig();
        agentExecutor = executionConfig.llmAgentExecutor();
        toolExecutor = executionConfig.llmToolExecutor();
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setAgentTimeout(Duration.ofSeconds(5));
        agentService = new AgentService(objectMapper, llmProperties, agentExecutor, toolExecutor);
    }

    @AfterEach
    void tearDown() {
        agentExecutor.close();
        toolExecutor.close();
    }

    @Test
    void openAISendsBothToolResultsWithThePreviousResponseId() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenAIProperties properties = new OpenAIProperties();
        properties.setModel("openai-test");
        properties.setMaxToolRounds(2);
        OpenAIService service = new OpenAIService(
                restClientBuilder.build(), properties, objectMapper, agentService);

        server.expect(requestTo(BASE_URL + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.input[0].content").value("Question"))
                .andExpect(jsonPath("$.previous_response_id").doesNotExist())
                .andExpect(jsonPath("$.parallel_tool_calls").value(true))
                .andRespond(withSuccess("""
                        {
                          "id": "resp_1",
                          "model": "openai-test",
                          "status": "completed",
                          "output": [
                            {"type":"function_call","call_id":"call_1","name":"first","arguments":"{\\\"value\\\":\\\"x\\\"}"},
                            {"type":"function_call","call_id":"call_2","name":"second","arguments":"{\\\"value\\\":\\\"y\\\"}"}
                          ],
                          "usage": {"input_tokens":10,"output_tokens":2,"total_tokens":12}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.previous_response_id").value("resp_1"))
                .andExpect(jsonPath("$.instructions").value("Be helpful"))
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andExpect(jsonPath("$.input[0].call_id").value("call_1"))
                .andExpect(jsonPath("$.input[0].output").value("first:x"))
                .andExpect(jsonPath("$.input[1].call_id").value("call_2"))
                .andExpect(jsonPath("$.input[1].output").value("second:y"))
                .andRespond(withSuccess("""
                        {
                          "id": "resp_2",
                          "model": "openai-test",
                          "status": "completed",
                          "output": [{"type":"message","content":[{"type":"output_text","text":"Done"}]}],
                          "usage": {"input_tokens":20,"output_tokens":3,"total_tokens":23}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResult<String> result = service.runChatWithTools(
                List.of(new LlmMessage("user", "Question")), "Be helpful", tools());

        assertThat(result.output()).isEqualTo("Done");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(5);
        server.verify();
    }

    @Test
    void openRouterSendsAssistantToolCallsAndResultsAsChatMessages() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setModel("openrouter-test");
        properties.setMaxToolRounds(2);
        OpenRouterService service = new OpenRouterService(
                restClientBuilder.build(), properties, objectMapper, agentService);

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Be helpful"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Question"))
                .andExpect(jsonPath("$.previous_response_id").doesNotExist())
                .andExpect(jsonPath("$.parallel_tool_calls").value(true))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "id": "router_resp_1",
                          "model": "openrouter-test",
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": null,
                              "tool_calls": [
                                {"id":"call_1","type":"function","function":{"name":"first","arguments":"{\\\"value\\\":\\\"x\\\"}"}},
                                {"id":"call_2","type":"function","function":{"name":"second","arguments":"{\\\"value\\\":\\\"y\\\"}"}}
                              ]
                            },
                            "finish_reason": "tool_calls"
                          }],
                          "usage": {"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.previous_response_id").doesNotExist())
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.messages.length()").value(5))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls.length()").value(2))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("first"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].content").value("first:x"))
                .andExpect(jsonPath("$.messages[4].role").value("tool"))
                .andExpect(jsonPath("$.messages[4].tool_call_id").value("call_2"))
                .andExpect(jsonPath("$.messages[4].content").value("second:y"))
                .andRespond(withSuccess("""
                        {
                          "id": "router_resp_2",
                          "model": "openrouter-test",
                          "choices": [{
                            "message": {"role":"assistant","content":"Done"},
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens":20,"completion_tokens":3,"total_tokens":23}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResult<String> result = service.runChatWithTools(
                List.of(new LlmMessage("user", "Question")), "Be helpful", tools());

        assertThat(result.output()).isEqualTo("Done");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(5);
        server.verify();
    }

    @Test
    void ollamaUsesTheSharedChatCompletionsToolLoop() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OllamaProperties properties = new OllamaProperties();
        properties.setModel("llama-test");
        properties.setMaxToolRounds(2);
        OllamaService service = new OllamaService(
                restClientBuilder.build(), properties, objectMapper, agentService);

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Be helpful"))
                .andExpect(jsonPath("$.messages[1].content").value("Question"))
                .andExpect(jsonPath("$.parallel_tool_calls").value(false))
                .andRespond(withSuccess("""
                        {
                          "id": "ollama_resp_1",
                          "model": "llama-test",
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": null,
                              "tool_calls": [
                                {"id":"call_1","type":"function","function":{"name":"first","arguments":"{\\\"value\\\":\\\"x\\\"}"}},
                                {"id":"call_2","type":"function","function":{"name":"second","arguments":"{\\\"value\\\":\\\"y\\\"}"}}
                              ]
                            },
                            "finish_reason": "tool_calls"
                          }],
                          "usage": {"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages.length()").value(5))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls.length()").value(2))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[4].tool_call_id").value("call_2"))
                .andRespond(withSuccess("""
                        {
                          "id": "ollama_resp_2",
                          "model": "llama-test",
                          "choices": [{
                            "message": {"role":"assistant","content":"Done"},
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens":20,"completion_tokens":3,"total_tokens":23}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResult<String> result = service.runChatWithTools(
                List.of(new LlmMessage("user", "Question")), "Be helpful", tools());

        assertThat(result.output()).isEqualTo("Done");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(5);
        server.verify();
    }

    @Test
    void groqUsesTheSharedChatCompletionsToolLoop() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GroqProperties properties = new GroqProperties();
        properties.setModel("openai/gpt-oss-20b");
        properties.setMaxToolRounds(2);
        GroqService service = new GroqService(
                restClientBuilder.build(), properties, objectMapper, agentService);

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Be helpful"))
                .andExpect(jsonPath("$.messages[1].content").value("Question"))
                .andExpect(jsonPath("$.parallel_tool_calls").value(true))
                .andRespond(withSuccess("""
                        {
                          "id": "groq_resp_1",
                          "model": "openai/gpt-oss-20b",
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": null,
                              "tool_calls": [
                                {"id":"call_1","type":"function","function":{"name":"first","arguments":"{\\\"value\\\":\\\"x\\\"}"}},
                                {"id":"call_2","type":"function","function":{"name":"second","arguments":"{\\\"value\\\":\\\"y\\\"}"}}
                              ]
                            },
                            "finish_reason": "tool_calls"
                          }],
                          "usage": {"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages.length()").value(5))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls.length()").value(2))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[4].tool_call_id").value("call_2"))
                .andRespond(withSuccess("""
                        {
                          "id": "groq_resp_2",
                          "model": "openai/gpt-oss-20b",
                          "choices": [{
                            "message": {"role":"assistant","content":"Done"},
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens":20,"completion_tokens":3,"total_tokens":23}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResult<String> result = service.runChatWithTools(
                List.of(new LlmMessage("user", "Question")), "Be helpful", tools());

        assertThat(result.output()).isEqualTo("Done");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(5);
        server.verify();
    }

    @Test
    void geminiSendsBothFunctionResultsWithThePreviousInteractionId() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GeminiProperties properties = new GeminiProperties();
        properties.setModel("gemini-test");
        properties.setMaxToolRounds(2);
        GeminiService service = new GeminiService(
                restClientBuilder.build(), properties, objectMapper, agentService);

        server.expect(requestTo(BASE_URL + "/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.input").value("Question"))
                .andExpect(jsonPath("$.previous_interaction_id").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "id": "interaction_1",
                          "status": "requires_action",
                          "model": "gemini-test",
                          "steps": [
                            {"id":"call_1","type":"function_call","name":"first","arguments":{"value":"x"}},
                            {"id":"call_2","type":"function_call","name":"second","arguments":{"value":"y"}}
                          ],
                          "usage": {"total_input_tokens":10,"total_output_tokens":2,"total_tokens":12}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.previous_interaction_id").value("interaction_1"))
                .andExpect(jsonPath("$.system_instruction").value("Be helpful"))
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andExpect(jsonPath("$.input[0].call_id").value("call_1"))
                .andExpect(jsonPath("$.input[0].result[0].text").value("first:x"))
                .andExpect(jsonPath("$.input[1].call_id").value("call_2"))
                .andExpect(jsonPath("$.input[1].result[0].text").value("second:y"))
                .andRespond(withSuccess("""
                        {
                          "id": "interaction_2",
                          "status": "completed",
                          "model": "gemini-test",
                          "steps": [{"type":"model_output","content":[{"type":"text","text":"Done"}]}],
                          "usage": {"total_input_tokens":20,"total_output_tokens":3,"total_tokens":23}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResult<String> result = service.runChatWithTools(
                List.of(new LlmMessage("user", "Question")), "Be helpful", tools());

        assertThat(result.output()).isEqualTo("Done");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(5);
        server.verify();
    }

    @Test
    void claudeSendsBothToolResultsInOneFollowingUserMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ClaudeProperties properties = new ClaudeProperties();
        properties.setModel("claude-test");
        properties.setMaxToolRounds(2);
        ClaudeService service = new ClaudeService(
                restClientBuilder.build(), properties, objectMapper, agentService);

        server.expect(requestTo(BASE_URL + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages[0].content").value("Question"))
                .andRespond(withSuccess("""
                        {
                          "id": "message_1",
                          "role": "assistant",
                          "model": "claude-test",
                          "content": [
                            {"type":"tool_use","id":"call_1","name":"first","input":{"value":"x"}},
                            {"type":"tool_use","id":"call_2","name":"second","input":{"value":"y"}}
                          ],
                          "stop_reason": "tool_use",
                          "usage": {"input_tokens":10,"output_tokens":2}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.system").value("Be helpful"))
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].role").value("user"))
                .andExpect(jsonPath("$.messages[2].content.length()").value(2))
                .andExpect(jsonPath("$.messages[2].content[0].tool_use_id").value("call_1"))
                .andExpect(jsonPath("$.messages[2].content[0].content").value("first:x"))
                .andExpect(jsonPath("$.messages[2].content[1].tool_use_id").value("call_2"))
                .andExpect(jsonPath("$.messages[2].content[1].content").value("second:y"))
                .andRespond(withSuccess("""
                        {
                          "id": "message_2",
                          "role": "assistant",
                          "model": "claude-test",
                          "content": [{"type":"text","text":"Done"}],
                          "stop_reason": "end_turn",
                          "usage": {"input_tokens":20,"output_tokens":3}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmResult<String> result = service.runChatWithTools(
                List.of(new LlmMessage("user", "Question")), "Be helpful", tools());

        assertThat(result.output()).isEqualTo("Done");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(5);
        server.verify();
    }

    private List<LlmTool<?>> tools() {
        return List.of(
                new LlmTool<>(
                        "first",
                        "First test tool",
                        Parameters.class,
                        parameters -> "first:" + parameters.value(),
                        true),
                new LlmTool<>(
                        "second",
                        "Second test tool",
                        Parameters.class,
                        parameters -> "second:" + parameters.value(),
                        true));
    }

    private record Parameters(String value) {
    }
}
