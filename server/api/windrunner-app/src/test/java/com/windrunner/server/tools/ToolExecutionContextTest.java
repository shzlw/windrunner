package com.windrunner.server.tools;

import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutionContextTest {

    @Test
    void normalizesAndFreezesAllowedProjects() {
        AppUser actor = new AppUser();
        ToolExecutionContext context = new ToolExecutionContext(
                actor, " session-1 ", List.of(" project-1 ", "project-1"));

        assertThat(context.chatSessionId()).isEqualTo("session-1");
        assertThat(context.allowedProjectIds()).containsExactly("project-1");
        assertThat(context.requireProjectId(" project-1 ")).isEqualTo("project-1");
    }

    @Test
    void rejectsProjectOutsideTheRequestScope() {
        ToolExecutionContext context = new ToolExecutionContext(new AppUser(), "session-1", List.of("project-1"));

        assertThatThrownBy(() -> context.requireProjectId("project-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not in the selected context");
    }

    @Test
    void rejectsBlankProjectIds() {
        ToolExecutionContext context = new ToolExecutionContext(new AppUser(), "session-1", List.of("project-1"));

        assertThatThrownBy(() -> context.requireProjectId(" "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("projectId is required");
    }
}
