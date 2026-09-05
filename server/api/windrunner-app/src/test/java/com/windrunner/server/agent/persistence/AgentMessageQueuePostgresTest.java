package com.windrunner.server.agent.persistence;

import com.windrunner.server.agent.domain.AgentMessageRoute;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.domain.ChatMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// Run against an isolated PostgreSQL database using AGENT_QUEUE_TEST_URL (JDBC URL).
// Every test class run creates and drops its own schema; no application data is used.
@EnabledIfEnvironmentVariable(named = "AGENT_QUEUE_TEST_URL", matches = ".+")
@SpringJUnitConfig(AgentMessageQueuePostgresTest.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class AgentMessageQueuePostgresTest {
    private static final String SCHEMA = "agent_queue_test_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired AgentMessageRequestRepository requests;
    @Autowired ChatMessageRepository messages;
    @Autowired NamedParameterJdbcOperations jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    private TransactionTemplate transactions;

    @BeforeEach
    void clear() {
        transactions = new TransactionTemplate(transactionManager);
        jdbc.getJdbcOperations().execute("TRUNCATE agent_message_route, chat_message RESTART IDENTITY");
    }

    @AfterAll
    void dropSchema() {
        jdbc.getJdbcOperations().execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void routingWaitsForTheEarlierUserMessageButOtherUsersProceed() {
        enqueue("a", "user-1");
        enqueue("b", "user-1");
        enqueue("c", "user-2");
        assertThat(claimRouting("b", "user-1")).isZero();
        assertThat(claimRouting("a", "user-1")).isOne();
        assertThat(claimRouting("c", "user-2")).isOne();
        assertThat(claimRouting("b", "user-1")).isZero();
        routed("a", "chat-x");
        assertThat(claimRouting("b", "user-1")).isOne();
        assertThat(requests.findById("a").orElseThrow().getContextIds()).containsExactly("context-1");
    }

    @Test
    void executionWaitsWithinAChatAndRunsAcrossChats() {
        for (String id : List.of("a", "b", "c")) {
            enqueue(id, "user-1");
            assertThat(claimRouting(id, "user-1")).isOne();
            routed(id, id.equals("b") ? "chat-y" : "chat-x");
        }
        assertThat(claimProcessing("a", "chat-x")).isOne();
        assertThat(claimProcessing("b", "chat-y")).isOne();
        assertThat(claimProcessing("c", "chat-x")).isZero();
        assertThat(requests.findById("c").orElseThrow().getStatus()).isEqualTo("ROUTED");
        transactions.executeWithoutResult(status -> {
            assertThat(requests.lockOwned("a", "user-1", "PROCESSING")).isPresent();
            assertThat(requests.markCompleted("a", "assistant-a")).isOne();
        });
        assertThat(claimProcessing("c", "chat-x")).isOne();
    }

    @Test
    void uncommittedEarlierInsertionCannotBeOvertaken() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                requests.lockIntake("user-1");
                requests.insert("a", "user-1", "a", "first");
                inserted.countDown();
                try {
                    if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
                Boolean locked = transactions.execute(status -> requests.tryLockQueue("agent-intake:user-1"));
                assertThat(locked).isFalse();
                var second = executor.submit(() -> enqueue("b", "user-1"));
                commit.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                assertThat(requests.findById("a").orElseThrow().getIngestionSequence())
                        .isLessThan(requests.findById("b").orElseThrow().getIngestionSequence());
                assertThat(claimRouting("b", "user-1")).isZero();
            } finally {
                commit.countDown();
            }
        }
    }

    @Test
    void competingWorkersClaimOneRequestOnlyOnce() throws Exception {
        enqueue("a", "user-1");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 12).mapToObj(index -> executor.submit(() -> {
                start.await();
                return claimRouting("a", "user-1");
            })).toList();
            start.countDown();
            int claims = 0;
            for (var task : tasks) claims += task.get(10, TimeUnit.SECONDS);
            assertThat(claims).isOne();
        }
    }

    @Test
    void expiredAttemptFailsAndReleasesTheQueueWithoutReplayingIt() {
        enqueue("a", "user-1");
        enqueue("b", "user-1");
        claimRouting("a", "user-1");
        jdbc.getJdbcOperations().update("UPDATE agent_message_route SET lease_until = NOW() - INTERVAL '1 second' WHERE id = 'a'");
        assertThat(requests.failExpired()).isOne();
        assertThat(requests.lockOwned("a", "user-1", "ROUTING")).isEmpty();
        assertThat(requests.findById("a").orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThat(claimRouting("a", "user-1")).isZero();
        assertThat(claimRouting("b", "user-1")).isOne();
    }

    @Test
    void finalizationRollsBackAsAUnitAndDuplicatesKeepTheOriginalRequest() {
        enqueue("a", "user-1");
        claimRouting("a", "user-1");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            assertThat(requests.lockOwned("a", "user-1", "ROUTING")).isPresent();
            messages.insert("source-a", "chat-x", "user", "a");
            routed("a", "chat-x");
            throw new IllegalStateException("Simulated persistence failure");
        })).isInstanceOf(IllegalStateException.class);
        AgentMessageRoute request = requests.findById("a").orElseThrow();
        assertThat(request.getStatus()).isEqualTo("ROUTING");
        assertThat(request.getSourceMessageId()).isNull();
        assertThat(messages.findByIdAndSessionId("source-a", "chat-x")).isEmpty();
        assertThat(requests.insert("duplicate", "user-1", "a", "a")).isZero();
        assertThat(requests.findByKey("user-1", "a").orElseThrow().getId()).isEqualTo("a");
    }

    @Test
    void historyPlacesEarlierAnswersBeforeTheQueuedQuestionAndExcludesLaterQuestions() {
        for (String id : List.of("a", "b", "c")) {
            enqueue(id, "user-1");
            claimRouting(id, "user-1");
            messages.insert("source-" + id, "chat-x", "user", id);
            routed(id, "chat-x");
        }
        claimProcessing("a", "chat-x");
        messages.insert("answer-a", "chat-x", "assistant", "Answer A");
        requests.markCompleted("a", "answer-a");
        AgentMessageRoute second = requests.findById("b").orElseThrow();
        ChatMessage source = messages.findByIdAndSessionId("source-b", "chat-x").orElseThrow();
        assertThat(messages.findForAgentRequest("chat-x", second.getIngestionSequence(), source.getCreatedAt(), 50))
                .extracting(ChatMessage::getId).containsExactly("source-a", "answer-a", "source-b");
    }

    private void enqueue(String id, String userId) {
        transactions.executeWithoutResult(status -> {
            requests.lockIntake(userId);
            requests.insert(id, userId, id, id);
        });
    }

    private int claimRouting(String id, String userId) {
        return transactions.execute(status -> {
            if (!requests.tryLockQueue("agent-intake:" + userId)) return 0;
            return requests.claimRouting(id, userId, 60);
        });
    }

    private int claimProcessing(String id, String chatId) {
        AgentMessageRoute request = requests.findById(id).orElseThrow();
        return transactions.execute(status -> {
            if (!requests.tryLockQueue("agent-execution:" + chatId)) return 0;
            return requests.claimProcessing(id, request.getUserId(), request.getIngestionSequence(), 60);
        });
    }

    private void routed(String id, String chatId) {
        assertThat(requests.markRouted(id, chatId, "CREATE", "source-" + id, new String[]{"context-1"})).isOne();
    }

    @Configuration
    @EnableJdbcRepositories(basePackageClasses = {AgentMessageRequestRepository.class, ChatMessageRepository.class})
    static class Config extends AbstractJdbcConfiguration {
        @Bean
        DataSource dataSource() throws Exception {
            String url = System.getenv("AGENT_QUEUE_TEST_URL");
            DriverManagerDataSource initial = new DriverManagerDataSource(url);
            try (var connection = initial.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + SCHEMA);
            }
            DriverManagerDataSource source = new DriverManagerDataSource(
                    url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__schema_update.sql"))
                    .execute(source);
            new org.springframework.jdbc.core.JdbcTemplate(source).execute("""
                    CREATE TABLE chat_message (
                        id TEXT PRIMARY KEY, chat_session_id TEXT NOT NULL, role TEXT NOT NULL,
                        content TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            return source;
        }

        @Bean
        NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource source) {
            return new NamedParameterJdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
