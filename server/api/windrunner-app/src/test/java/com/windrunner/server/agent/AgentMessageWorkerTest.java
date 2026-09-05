package com.windrunner.server.agent;

import com.windrunner.server.agent.domain.AgentMessageRoute;
import com.windrunner.server.agent.persistence.AgentMessageRequestRepository;
import com.windrunner.server.llm.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentMessageWorkerTest {
    @Test
    void busyRequestsStayPendingAndDoNotCallTheLlm() {
        AgentMessageRequestRepository requests = mock(AgentMessageRequestRepository.class);
        AgentMessageService service = mock(AgentMessageService.class);
        AgentMessageWorker worker = worker(requests, service);
        AgentMessageRoute request = request();
        when(requests.tryLockQueue("agent-intake:user-1")).thenReturn(true);
        try {
            worker.process(request, true);
            verifyNoInteractions(service);
            verify(requests, never()).markFailed(anyString(), anyString(), anyString());
        } finally {
            worker.close();
        }
    }

    @Test
    void executionFailureReleasesOnlyTheClaimedStage() {
        AgentMessageRequestRepository requests = mock(AgentMessageRequestRepository.class);
        AgentMessageService service = mock(AgentMessageService.class);
        AgentMessageWorker worker = worker(requests, service);
        AgentMessageRoute request = request();
        when(requests.tryLockQueue("agent-execution:chat-1")).thenReturn(true);
        when(requests.claimProcessing("request-1", "user-1", 1, 90)).thenReturn(1);
        doThrow(new IllegalStateException("Provider failed")).when(service).execute(request);
        try {
            worker.process(request, false);
            verify(requests).markFailed("request-1", "PROCESSING", "Message processing failed");
        } finally {
            worker.close();
        }
    }

    private AgentMessageWorker worker(AgentMessageRequestRepository requests, AgentMessageService service) {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        LlmProperties properties = new LlmProperties();
        properties.setAgentTimeout(Duration.ofSeconds(60));
        return new AgentMessageWorker(requests, service, transactions, properties, 1, 1);
    }

    private AgentMessageRoute request() {
        AgentMessageRoute request = new AgentMessageRoute();
        request.setId("request-1");
        request.setUserId("user-1");
        request.setRoutedChatSessionId("chat-1");
        request.setIngestionSequence(1);
        return request;
    }
}
