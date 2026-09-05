package com.windrunner.server.agent.api;

import com.windrunner.server.agent.AgentMessageService;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAgentMessageControllerTest {
    @Test
    void acceptsOnlyAMessageInTheRequestBodyAndPassesTheIdempotencyKeySeparately() {
        AuthService auth = mock(AuthService.class);
        AgentMessageService service = mock(AgentMessageService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AppUser actor = new AppUser();
        actor.setId("user-1");
        AgentMessageResponse expected = new AgentMessageResponse(
                "request-1", "Done",
                new AgentMessageResponse.Routing("CREATE",
                        new AgentMessageResponse.ChatSessionReference(
                                "chat-session-1", "Hello")),
                "COMPLETED", List.of(), null);
        when(auth.requireCurrentUser(request)).thenReturn(actor);
        when(service.process(actor, "key-1", "Hello")).thenReturn(expected);

        InternalAgentMessageController controller = new InternalAgentMessageController(auth, service);
        var response = controller.send("key-1",
                new AgentMessageRequest("Hello"), request);

        assertThat(response.data()).isEqualTo(expected);
        assertThat(AgentMessageRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("message");
        verify(service).process(actor, "key-1", "Hello");
    }
    @Test
    void statusLookupUsesTheAuthenticatedUser() {
        AuthService auth = mock(AuthService.class);
        AgentMessageService service = mock(AgentMessageService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AppUser actor = new AppUser();
        when(auth.requireCurrentUser(request)).thenReturn(actor);
        new InternalAgentMessageController(auth, service).get("request-1", request);
        verify(service).get("request-1", actor);
    }

}
