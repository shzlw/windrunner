package com.windrunner.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.user.domain.AppUser;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ToolServiceTest {

    @Test
    void exposesRegisteredToolsAsLlmTools() {
        ToolService service = new ToolService(List.of(new TestTool("fetch_things")));

        assertThat(service.llmTools(context())).extracting(function -> function.name())
                .containsExactly("fetch_things");
    }

    @Test
    void rejectsDuplicateToolNames() {
        assertThatThrownBy(() -> new ToolService(List.of(
                new TestTool("duplicate"),
                new TestTool("duplicate")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate tool name: duplicate");
    }

    @Test
    void bindsTheRequestContextToEachLlmToolInvocation() throws Exception {
        AtomicReference<ToolExecutionContext> receivedContext = new AtomicReference<>();
        TestTool tool = new TestTool("fetch_things", receivedContext);
        ToolExecutionContext context = context();

        Object result = execute(new ToolService(List.of(tool)).llmTools(context).getFirst(), new Parameters("value"));

        assertThat(result).isEqualTo("value");
        assertThat(receivedContext).hasValue(context);
    }

    @Test
    void rejectsMissingRequestContext() {
        ToolService service = new ToolService(List.of(new TestTool("fetch_things")));

        assertThatThrownBy(() -> service.llmTools(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool execution context is required");
    }

    private record TestTool(String name, AtomicReference<ToolExecutionContext> receivedContext) implements Tool<Parameters> {

        private TestTool(String name) {
            this(name, new AtomicReference<>());
        }

        @Override
        public String description() {
            return "A test tool";
        }

        @Override
        public Class<Parameters> parametersType() {
            return Parameters.class;
        }

        @Override
        public Object execute(Parameters parameters, ToolExecutionContext context) {
            receivedContext.set(context);
            return parameters.value();
        }
    }

    private record Parameters(String value) {
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(new AppUser(), "session-1", List.of());
    }

    @SuppressWarnings("unchecked")
    private Object execute(LlmTool<?> tool, Parameters parameters) throws Exception {
        return ((LlmTool<Parameters>) tool).handler().execute(parameters);
    }
}
