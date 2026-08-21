package com.windrunner.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolServiceTest {

    @Test
    void exposesRegisteredToolsAsLlmTools() {
        ToolService service = new ToolService(List.of(new TestTool("fetch_things")));

        assertThat(service.llmTools()).extracting(function -> function.name())
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

    private record TestTool(String name) implements Tool<Parameters> {

        @Override
        public String description() {
            return "A test tool";
        }

        @Override
        public Class<Parameters> parametersType() {
            return Parameters.class;
        }

        @Override
        public Object execute(Parameters parameters) {
            return parameters.value();
        }
    }

    private record Parameters(String value) {
    }
}
