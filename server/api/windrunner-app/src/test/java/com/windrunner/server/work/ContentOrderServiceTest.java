package com.windrunner.server.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.work.api.ContentOrderItem;
import com.windrunner.server.work.api.ContentOrderItemRef;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ContentOrderServiceTest {
    @Mock private WorkItemRepository workItems;
    @Mock private EntryRepository entries;
    private ContentOrderService service;

    @BeforeEach
    void setUp() {
        service = new ContentOrderService(workItems, entries);
    }

    @Test
    void calculatesNextIndexAcrossBothTables() {
        when(workItems.maxSortIndex("project-1", "parent-1")).thenReturn(2000);
        when(entries.maxSortIndex("project-1", "parent-1")).thenReturn(5000);

        assertThat(service.nextSortIndex("project-1", "parent-1")).isEqualTo(6000);
    }

    @Test
    void reordersWorkItemsAndEntriesInOneSequence() {
        WorkItem task = new WorkItem();
        task.setId("work-1");
        task.setSortIndex(2000);
        Entry update = new Entry();
        update.setId("entry-1");
        update.setSortIndex(1000);
        when(workItems.existsInProject("parent-1", "project-1")).thenReturn(true);
        when(workItems.findByParent("project-1", "parent-1")).thenReturn(List.of(task));
        when(entries.findByWorkItemId("parent-1")).thenReturn(List.of(update));

        List<ContentOrderItem> reordered = service.reorder("project-1", "parent-1", List.of(
                new ContentOrderItemRef("WORK_ITEM", "work-1"),
                new ContentOrderItemRef("ENTRY", "entry-1")));

        assertThat(reordered).containsExactly(
                new ContentOrderItem("WORK_ITEM", "work-1", 1000),
                new ContentOrderItem("ENTRY", "entry-1", 2000));
        verify(workItems).updateSortIndex("work-1", "project-1", 1000);
        verify(entries).updateSortIndex("entry-1", "project-1", 2000);
    }

    @Test
    void rejectsPartialContentOrder() {
        WorkItem task = new WorkItem();
        task.setId("work-1");
        task.setSortIndex(2000);
        Entry update = new Entry();
        update.setId("entry-1");
        update.setSortIndex(1000);
        when(workItems.existsInProject("parent-1", "project-1")).thenReturn(true);
        when(workItems.findByParent("project-1", "parent-1")).thenReturn(List.of(task));
        when(entries.findByWorkItemId("parent-1")).thenReturn(List.of(update));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.reorder(
                "project-1", "parent-1", List.of(new ContentOrderItemRef("ENTRY", "entry-1"))));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Content order must include every item in the parent exactly once");
    }

    @Test
    void movesWorkItemAndPreservesBothMixedContentStreams() {
        WorkItem moved = new WorkItem();
        moved.setId("work-moved");
        moved.setSortIndex(1000);
        Entry sourceEntry = new Entry();
        sourceEntry.setId("entry-source");
        sourceEntry.setSortIndex(2000);
        WorkItem destinationWorkItem = new WorkItem();
        destinationWorkItem.setId("work-destination");
        destinationWorkItem.setSortIndex(1000);
        Entry destinationEntry = new Entry();
        destinationEntry.setId("entry-destination");
        destinationEntry.setSortIndex(2000);
        when(workItems.existsInProject("destination", "project-1")).thenReturn(true);
        when(workItems.findByParent("project-1", "source")).thenReturn(new java.util.ArrayList<>(List.of(moved)));
        when(entries.findByWorkItemId("source")).thenReturn(List.of(sourceEntry));
        when(workItems.findByParent("project-1", "destination")).thenReturn(List.of(destinationWorkItem));
        when(entries.findByWorkItemId("destination")).thenReturn(List.of(destinationEntry));
        when(workItems.updateParentAndSortIndex("work-moved", "project-1", "destination", 0)).thenReturn(1);

        service.moveWorkItem("project-1", "work-moved", "source", "destination", null, null);

        verify(workItems).updateParentAndSortIndex("work-moved", "project-1", "destination", 0);
        verify(entries).updateSortIndex("entry-source", "project-1", 1000);
        verify(workItems).updateSortIndex("work-destination", "project-1", 1000);
        verify(entries).updateSortIndex("entry-destination", "project-1", 2000);
        verify(workItems).updateSortIndex("work-moved", "project-1", 3000);
    }
}
