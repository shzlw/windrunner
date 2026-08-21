package com.windrunner.server.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock
    private RelationshipRepository relationships;
    @Mock
    private WorkItemService workItems;
    @Mock
    private EntryRepository entries;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private com.windrunner.server.search.SearchNormalizer searchNormalizer;
    @Mock
    private com.windrunner.server.notification.NotificationService notificationService;
    @Mock
    private com.windrunner.server.notification.WorkItemNotificationAudience notificationAudience;
    @Mock
    private com.windrunner.server.work.persistence.WorkItemRepository workItemRepository;
    private RelationshipService service;

    @BeforeEach
    void setUp() {
        service = new RelationshipService(relationships, workItems, entries, new EntityIdGenerator(), auditLogService, searchNormalizer, notificationService, notificationAudience, workItemRepository);
    }

    @Test
    void acceptingAnswerReplacesPreviousAnswerAndCompletesQuestion() {
        WorkItem question = question();
        Entry answer = answer("question-1");
        Relationship requested = acceptedAnswer();
        when(workItems.get("project-1", "question-1")).thenReturn(question);
        when(entries.findById("entry-1")).thenReturn(Optional.of(answer));
        when(relationships.findById(anyString())).thenAnswer(invocation -> {
            requested.setId(invocation.getArgument(0));
            return Optional.of(requested);
        });

        Relationship created = service.create("project-1", requested, "actor-1");

        assertThat(created.getId()).startsWith("rela_");
        assertThat(question.getStatus()).isEqualTo("ANSWERED");
        verify(relationships).deleteFromWorkItemByType("project-1", "question-1", "ACCEPTED_ANSWER");
        verify(relationships).insert(
                eq(created.getId()), eq("project-1"), eq("WORK_ITEM"), eq("question-1"),
                eq("ENTRY"), eq("entry-1"), eq("ACCEPTED_ANSWER"), isNull(), eq("entry-1"), eq("actor-1"), isNull());
        verify(workItems).update("project-1", "question-1", question, null, "actor-1");
    }

    @Test
    void acceptedAnswerMustBelongToQuestion() {
        when(workItems.get("project-1", "question-1")).thenReturn(question());
        when(entries.findById("entry-1")).thenReturn(Optional.of(answer("another-question")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create("project-1", acceptedAnswer(), "actor-1"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Accepted answer must belong to the question");
        verify(relationships, never()).insert(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private WorkItem question() {
        WorkItem question = new WorkItem();
        question.setId("question-1");
        question.setProjectId("project-1");
        question.setType("QUESTION");
        question.setTitle("Why did deployment fail?");
        question.setStatus("OPEN");
        return question;
    }

    private Entry answer(String workItemId) {
        Entry answer = new Entry();
        answer.setId("entry-1");
        answer.setProjectId("project-1");
        answer.setWorkItemId(workItemId);
        answer.setType("ANSWER");
        answer.setBody("The token format changed.");
        return answer;
    }

    private Relationship acceptedAnswer() {
        Relationship relationship = new Relationship();
        relationship.setFromEntityType("WORK_ITEM");
        relationship.setFromEntityId("question-1");
        relationship.setToEntityType("ENTRY");
        relationship.setToEntityId("entry-1");
        relationship.setType("ACCEPTED_ANSWER");
        relationship.setSourceEntryId("entry-1");
        return relationship;
    }
}
