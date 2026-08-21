package com.windrunner.server.work;

import com.windrunner.server.work.api.ContentOrderItem;
import com.windrunner.server.work.api.ContentOrderItemRef;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContentOrderService {
    private static final int SORT_GAP = 1000;

    private final WorkItemRepository workItems;
    private final EntryRepository entries;

    public int nextSortIndex(String projectId, String parentWorkItemId) {
        int workItemMaximum = workItems.maxSortIndex(projectId, parentWorkItemId);
        int entryMaximum = parentWorkItemId == null ? 0 : entries.maxSortIndex(projectId, parentWorkItemId);
        return Math.max(workItemMaximum, entryMaximum) + SORT_GAP;
    }

    @Transactional
    public List<ContentOrderItem> reorder(String projectId, String parentWorkItemId, List<ContentOrderItemRef> requestedOrder) {
        if (requestedOrder == null || requestedOrder.stream().anyMatch(java.util.Objects::isNull)) {
            throw WorkItemService.bad("Content order items are required");
        }
        if (parentWorkItemId != null && !workItems.existsInProject(parentWorkItemId, projectId)) {
            throw WorkItemService.notFound("Parent work item not found");
        }

        var currentByKey = new HashMap<String, ContentOrderItem>();
        contentItems(projectId, parentWorkItemId).forEach(item -> currentByKey.put(key(item.entityType(), item.entityId()), item));

        List<String> requestedKeys = requestedOrder.stream().map(item -> key(
                WorkItemService.enumValue(item.entityType(), WorkTypes.ENTITY_TYPES, "Content entity type"),
                item.entityId())).toList();
        Set<String> currentKeys = currentByKey.keySet();
        if (requestedKeys.size() != currentKeys.size()
                || new HashSet<>(requestedKeys).size() != requestedKeys.size()
                || !currentKeys.equals(new HashSet<>(requestedKeys))) {
            throw WorkItemService.bad("Content order must include every item in the parent exactly once");
        }

        return applyOrder(projectId, requestedKeys.stream().map(currentByKey::get).toList());
    }

    /**
     * Moves a WorkItem and reindexes the source and destination mixed-content streams as one transaction.
     */
    @Transactional
    public void moveWorkItem(String projectId, String workItemId, String sourceParentWorkItemId, String destinationParentWorkItemId,
                             String beforeEntityType, String beforeEntityId) {
        if (destinationParentWorkItemId != null && !workItems.existsInProject(destinationParentWorkItemId, projectId)) {
            throw WorkItemService.notFound("Parent work item not found");
        }

        List<ContentOrderItem> sourceItems = contentItems(projectId, sourceParentWorkItemId);
        ContentOrderItem movingItem = sourceItems.stream()
                .filter(item -> "WORK_ITEM".equals(item.entityType()) && workItemId.equals(item.entityId()))
                .findFirst()
                .orElseThrow(() -> WorkItemService.notFound("Work item not found"));
        List<ContentOrderItem> destinationItems = java.util.Objects.equals(sourceParentWorkItemId, destinationParentWorkItemId)
                ? new ArrayList<>(sourceItems)
                : contentItems(projectId, destinationParentWorkItemId);
        sourceItems.removeIf(item -> "WORK_ITEM".equals(item.entityType()) && workItemId.equals(item.entityId()));
        destinationItems.removeIf(item -> "WORK_ITEM".equals(item.entityType()) && workItemId.equals(item.entityId()));

        int destinationIndex = destinationItems.size();
        if (!WorkItemService.blank(beforeEntityId)) {
            String beforeKey = key(WorkItemService.enumValue(beforeEntityType, WorkTypes.ENTITY_TYPES, "Before content entity type"), beforeEntityId);
            destinationIndex = indexOf(destinationItems, beforeKey);
            if (destinationIndex < 0) throw WorkItemService.bad("Before content item is not in the destination");
        }
        destinationItems.add(destinationIndex, movingItem);

        if (workItems.updateParentAndSortIndex(workItemId, projectId, destinationParentWorkItemId, 0) == 0) {
            throw WorkItemService.notFound("Work item not found");
        }
        if (java.util.Objects.equals(sourceParentWorkItemId, destinationParentWorkItemId)) {
            applyOrder(projectId, destinationItems);
        } else {
            applyOrder(projectId, sourceItems);
            applyOrder(projectId, destinationItems);
        }
    }

    private List<ContentOrderItem> contentItems(String projectId, String parentWorkItemId) {
        List<ContentOrderItem> result = new ArrayList<>();
        workItems.findByParent(projectId, parentWorkItemId).forEach(item -> result.add(new ContentOrderItem("WORK_ITEM", item.getId(), item.getSortIndex())));
        if (parentWorkItemId != null)
            entries.findByWorkItemId(parentWorkItemId).forEach(entry -> result.add(new ContentOrderItem("ENTRY", entry.getId(), entry.getSortIndex())));
        result.sort(java.util.Comparator.comparingInt(ContentOrderItem::sortIndex).thenComparing(item -> key(item.entityType(), item.entityId())));
        return result;
    }

    private List<ContentOrderItem> applyOrder(String projectId, List<ContentOrderItem> items) {
        List<ContentOrderItem> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ContentOrderItem item = items.get(index);
            int sortIndex = (index + 1) * SORT_GAP;
            if ("WORK_ITEM".equals(item.entityType())) workItems.updateSortIndex(item.entityId(), projectId, sortIndex);
            else entries.updateSortIndex(item.entityId(), projectId, sortIndex);
            result.add(new ContentOrderItem(item.entityType(), item.entityId(), sortIndex));
        }
        return result;
    }

    private int indexOf(List<ContentOrderItem> items, String requestedKey) {
        for (int index = 0; index < items.size(); index++)
            if (requestedKey.equals(key(items.get(index).entityType(), items.get(index).entityId()))) return index;
        return -1;
    }

    private String key(String entityType, String entityId) {
        if (WorkItemService.blank(entityId)) throw WorkItemService.bad("Content entity id is required");
        return entityType + ":" + entityId;
    }
}
