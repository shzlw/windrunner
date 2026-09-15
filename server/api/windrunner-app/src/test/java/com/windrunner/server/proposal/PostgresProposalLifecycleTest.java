package com.windrunner.server.proposal;

import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.WorkItemDraft;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import com.windrunner.server.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Execution(ExecutionMode.SAME_THREAD)
class PostgresProposalLifecycleTest extends PostgresIntegrationTestSupport {

    private static final String ACTOR_ID = "pg-proposal-actor";
    private static final String PROJECT_ID = "pg-proposal-project";
    private static final String WORK_ITEM_ID = "pg-proposal-work-item";
    private static final String SESSION_ID = "pg-proposal-session";
    private static final String MESSAGE_ID = "pg-proposal-message";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkspaceChangeProposalService proposalService;

    @BeforeEach
    void setUp() {
        deleteFixtures();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, email, display_name, timezone, password_hash, status, global_role)
                VALUES (?, ?, ?, ?, 'UTC', 'unused', 'ACTIVE', 'USER')
                """, ACTOR_ID, "pg-proposal-actor", "pg-proposal-actor@example.com", "Proposal Actor");
        jdbcTemplate.update("""
                INSERT INTO project (id, name, created_by_user_id)
                VALUES (?, ?, ?)
                """, PROJECT_ID, "Postgres Proposal Tests", ACTOR_ID);
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, role)
                VALUES (?, ?, 'EDITOR')
                """, PROJECT_ID, ACTOR_ID);
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    @Test
    void createsAndAcceptsAWorkItemProposal() {
        WorkspaceChangeProposalView pending = proposalService.create(
                PROJECT_ID,
                SESSION_ID,
                MESSAGE_ID,
                "Create a work item",
                actor(),
                new ProposalDraft(List.of(new ChangeDraft(
                        "WORK_ITEM", "ADD", null, "new-item", "Create work item",
                        new WorkItemDraft("Created from a proposal", "TASK", "OPEN", null,
                                "HIGH", null, List.of()),
                        null,
                        null))));

        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.changes()).hasSize(1);
        String createdWorkItemId = pending.changes().getFirst().targetId();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal WHERE id = ?", String.class, pending.id()))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_item WHERE id = ?", Long.class, createdWorkItemId))
                .isZero();

        WorkspaceChangeProposalView applied = proposalService.decideAll(
                PROJECT_ID, pending.id(), new DecisionRequest("ACCEPT", null), actor());

        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(applied.changes()).singleElement()
                .extracting(WorkspaceChangeProposalView.ChangeView::status)
                .isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM work_item WHERE id = ?", String.class, createdWorkItemId))
                .isEqualTo("Created from a proposal");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal_change WHERE proposal_id = ?", String.class, pending.id()))
                .isEqualTo("APPLIED");
    }

    @Test
    void rejectsAWorkItemUpdateThatWouldNotChangeTheTarget() {
        seedWorkItem("Original title");

        assertThatThrownBy(() -> createTitleUpdate("Original title"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal WHERE project_id = ?", Long.class, PROJECT_ID))
                .isZero();
    }

    @Test
    void createsAndAcceptsParentBeforeChildWorkItems() {
        WorkspaceChangeProposalView pending = proposalService.create(
                PROJECT_ID,
                SESSION_ID,
                MESSAGE_ID,
                "Create a work item hierarchy",
                actor(),
                new ProposalDraft(List.of(
                        new ChangeDraft(
                                "WORK_ITEM", "ADD", null, "parent", "Create parent",
                                new WorkItemDraft("Parent", "TASK", "OPEN", null, null, null, List.of()),
                                null,
                                null),
                        new ChangeDraft(
                                "WORK_ITEM", "ADD", null, "child", "Create child",
                                new WorkItemDraft("Child", "TASK", "OPEN", null, null, "parent", List.of()),
                                null,
                                null))));

        String parentId = pending.changes().get(0).targetId();
        String childId = pending.changes().get(1).targetId();
        WorkspaceChangeProposalView applied = proposalService.decideAll(
                PROJECT_ID, pending.id(), new DecisionRequest("ACCEPT", null), actor());

        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT parent_work_item_id FROM work_item WHERE id = ?", String.class, childId))
                .isEqualTo(parentId);
    }

    @Test
    void rejectsAWorkItemUpdateWithoutMutatingTheTarget() {
        seedWorkItem("Original title");
        WorkspaceChangeProposalView pending = createTitleUpdate("Rejected title");

        WorkspaceChangeProposalView rejected = proposalService.decideAll(
                PROJECT_ID, pending.id(), new DecisionRequest("REJECT", null), actor());

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM work_item WHERE id = ?", String.class, WORK_ITEM_ID))
                .isEqualTo("Original title");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal_change WHERE proposal_id = ?", String.class, pending.id()))
                .isEqualTo("REJECTED");
    }

    @Test
    void rejectsAcceptanceWhenTheTargetChangedAfterProposalCreation() {
        seedWorkItem("Original title");
        WorkspaceChangeProposalView pending = createTitleUpdate("Suggested title");
        jdbcTemplate.update("""
                UPDATE work_item
                SET title = ?, updated_at = updated_at + INTERVAL '1 second'
                WHERE id = ?
                """, "Changed elsewhere", WORK_ITEM_ID);

        assertThatThrownBy(() -> proposalService.decideAll(
                PROJECT_ID, pending.id(), new DecisionRequest("ACCEPT", null), actor()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM work_item WHERE id = ?", String.class, WORK_ITEM_ID))
                .isEqualTo("Changed elsewhere");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal WHERE id = ?", String.class, pending.id()))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal_change WHERE proposal_id = ?", String.class, pending.id()))
                .isEqualTo("PENDING");
    }

    @Test
    void createsAndAcceptsARevertForAnAppliedUpdate() {
        seedWorkItem("Original title");
        WorkspaceChangeProposalView applied = proposalService.decideAll(
                PROJECT_ID,
                createTitleUpdate("Updated title").id(),
                new DecisionRequest("ACCEPT", null),
                actor());

        WorkspaceChangeProposalView revert = proposalService.createRevert(
                PROJECT_ID, applied.id(), actor());

        assertThat(revert.status()).isEqualTo("PENDING");
        assertThat(revert.revertsProposalId()).isEqualTo(applied.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal WHERE reverts_proposal_id = ?", Long.class, applied.id()))
                .isEqualTo(1L);

        WorkspaceChangeProposalView reverted = proposalService.decideAll(
                PROJECT_ID, revert.id(), new DecisionRequest("ACCEPT", null), actor());

        assertThat(reverted.status()).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM work_item WHERE id = ?", String.class, WORK_ITEM_ID))
                .isEqualTo("Original title");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal WHERE id = ?", String.class, applied.id()))
                .isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal WHERE id = ?", String.class, revert.id()))
                .isEqualTo("APPLIED");
    }

    @Test
    void allowsOnlyOneConcurrentAcceptance() throws Exception {
        seedWorkItem("Original title");
        WorkspaceChangeProposalView pending = createTitleUpdate("Suggested title");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(createConcurrentAcceptance(
                    pending.id(), ready, start));
            Future<Object> second = executor.submit(createConcurrentAcceptance(
                    pending.id(), ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Object firstResult = getResult(first);
            Object secondResult = getResult(second);

            List<Object> results = List.of(firstResult, secondResult);
            assertThat(results).filteredOn(WorkspaceChangeProposalView.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof ResponseStatusException).hasSize(1);
            Object failure = results.stream()
                    .filter(result -> result instanceof ResponseStatusException)
                    .findFirst()
                    .orElseThrow();
            assertThat((ResponseStatusException) failure).extracting(ResponseStatusException::getStatusCode)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM proposal WHERE id = ?", String.class, pending.id()))
                .isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_change WHERE proposal_id = ? AND status = 'APPLIED'",
                Long.class, pending.id()))
                .isEqualTo(1L);
    }

    private Callable<Object> createConcurrentAcceptance(String proposalId,
                                                        CountDownLatch ready,
                                                        CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                return proposalService.decideAll(
                        PROJECT_ID, proposalId, new DecisionRequest("ACCEPT", null), actor());
            } catch (ResponseStatusException exception) {
                return exception;
            }
        };
    }

    private Object getResult(Future<Object> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(30, TimeUnit.SECONDS);
    }

    private WorkspaceChangeProposalView createTitleUpdate(String title) {
        return proposalService.create(
                PROJECT_ID,
                SESSION_ID,
                MESSAGE_ID,
                "Update the title",
                actor(),
                new ProposalDraft(List.of(new ChangeDraft(
                        "WORK_ITEM", "UPDATE", WORK_ITEM_ID, null, "Update work item title",
                        new WorkItemDraft(title, null, null, null, null, null, null),
                        null,
                        null))));
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId(ACTOR_ID);
        actor.setUsername("pg-proposal-actor");
        actor.setStatus("ACTIVE");
        actor.setGlobalRole("USER");
        return actor;
    }

    private void seedWorkItem(String title) {
        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        jdbcTemplate.update("""
                INSERT INTO work_item (
                    id, project_id, parent_work_item_id, sort_index, type, title, status,
                    due_date, priority, created_by_user_id, search_vec, created_at, updated_at
                )
                VALUES (?, ?, NULL, 0, 'TASK', ?, 'OPEN', NULL, NULL, ?,
                        to_tsvector('simple', ?), ?, ?)
                """, WORK_ITEM_ID, PROJECT_ID, title, ACTOR_ID, title, timestamp, timestamp);
    }

    private void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM user_notification WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM mail_notification WHERE recipient_user_id = ? OR actor_user_id = ?",
                ACTOR_ID, ACTOR_ID);
        jdbcTemplate.update("DELETE FROM audit_log WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM proposal_change WHERE proposal_id IN (SELECT id FROM proposal WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM proposal WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM relationship WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM entry WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM calendar_event WHERE user_id = ?", ACTOR_ID);
        jdbcTemplate.update("DELETE FROM work_item_subscription WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM work_item_assignee WHERE work_item_id IN (SELECT id FROM work_item WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM work_item WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project_member WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", ACTOR_ID);
    }
}
