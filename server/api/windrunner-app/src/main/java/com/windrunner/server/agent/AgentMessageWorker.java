package com.windrunner.server.agent;

import com.windrunner.server.agent.domain.AgentMessageRoute;
import com.windrunner.server.agent.persistence.AgentMessageRequestRepository;
import com.windrunner.server.llm.LlmProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AgentMessageWorker {
    private final AgentMessageRequestRepository requests;
    private final AgentMessageService service;
    private final TransactionTemplate transactions;
    private final long leaseSeconds;
    private final ThreadPoolExecutor routers;
    private final ThreadPoolExecutor processors;

    public AgentMessageWorker(AgentMessageRequestRepository requests, AgentMessageService service,
                              TransactionTemplate transactions, LlmProperties llmProperties,
                              @Value("${windrunner.agent.routing-workers}") int routingWorkers,
                              @Value("${windrunner.agent.processing-workers}") int processingWorkers) {
        this.requests = requests;
        this.service = service;
        this.transactions = transactions;
        // A fixed deadline is enough because the LLM loop already has a total timeout.
        // Expired attempts fail rather than replaying potentially persisted proposals.
        this.leaseSeconds = llmProperties.getAgentTimeout().plusSeconds(30).toSeconds();
        this.routers = executor(routingWorkers, "agent-router-");
        this.processors = executor(processingWorkers, "agent-processor-");
    }

    private ThreadPoolExecutor executor(int size, String name) {
        if (size < 1) throw new IllegalArgumentException("Agent worker counts must be positive");
        return new ThreadPoolExecutor(size, size, 0, TimeUnit.SECONDS,
                new SynchronousQueue<>(), Thread.ofPlatform().name(name, 0).factory());
    }

    @Scheduled(fixedDelay = 500)
    public void dispatch() {
        requests.failExpired();
        dispatch(routers, true);
        dispatch(processors, false);
    }

    private void dispatch(ThreadPoolExecutor executor, boolean routing) {
        int capacity = executor.getMaximumPoolSize() - executor.getActiveCount();
        if (capacity <= 0 || executor.isShutdown()) return;
        var candidates = routing ? requests.findRoutingCandidates(capacity)
                : requests.findProcessingCandidates(capacity);
        for (AgentMessageRoute request : candidates) {
            try {
                // Claim inside the running task: work never holds a lease while queued locally.
                executor.execute(() -> process(request, routing));
            } catch (RejectedExecutionException exception) {
                break; // Another task took the slot; the database request stays pending.
            }
        }
    }

    void process(AgentMessageRoute request, boolean routing) {
        boolean claimed = false;
        String status = routing ? "ROUTING" : "PROCESSING";
        try {
            claimed = Boolean.TRUE.equals(transactions.execute(transaction -> {
                String key = routing ? "agent-intake:" + request.getUserId()
                        : "agent-execution:" + request.getRoutedChatSessionId();
                if (!requests.tryLockQueue(key)) return false;
                int updated = routing
                        ? requests.claimRouting(request.getId(), request.getUserId(), leaseSeconds)
                        : requests.claimProcessing(request.getId(), request.getUserId(),
                                request.getIngestionSequence(), leaseSeconds);
                return updated == 1;
            }));
            if (!claimed) return;
            if (routing) service.route(request);
            else service.execute(request);
        } catch (RuntimeException exception) {
            log.warn("Agent message failed requestId={}, stage={}", request.getId(), status, exception);
            if (claimed) {
                requests.markFailed(request.getId(), status,
                        routing ? "Message routing failed" : "Message processing failed");
            }
        }
    }

    @PreDestroy
    public void close() {
        routers.shutdownNow();
        processors.shutdownNow();
    }
}
