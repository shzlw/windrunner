package com.windrunner.server.work;

import com.windrunner.server.work.api.ProjectSearchResult;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ProjectSearchService {
    private static final int DEFAULT_LIMIT = 20;

    private final WorkItemRepository workItems;
    private final EntryRepository entries;
    private final RelationshipRepository relationships;
    private final com.windrunner.server.search.SearchNormalizer searchNormalizer;

    public ProjectSearchResult search(String projectId, String query, Integer limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new ProjectSearchResult(List.of(), List.of(), List.of());
        }
        int normalizedLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, 100));
        String ftsQuery = searchNormalizer.normalize(trimmed);
        List<WorkItem> matchedItems = workItems.searchInProject(projectId, ftsQuery, trimmed, normalizedLimit);
        List<Entry> matchedEntries = entries.searchInProject(projectId, ftsQuery, trimmed, normalizedLimit);
        List<Relationship> matchedRelationships = relationships.searchInProject(projectId, ftsQuery, trimmed, normalizedLimit);

        Set<String> referencedItemIds = new LinkedHashSet<>();
        matchedItems.forEach(item -> referencedItemIds.add(item.getId()));
        matchedEntries.forEach(entry -> referencedItemIds.add(entry.getWorkItemId()));
        matchedRelationships.forEach(relationship -> {
            if ("WORK_ITEM".equals(relationship.getFromEntityType())) referencedItemIds.add(relationship.getFromEntityId());
            if ("WORK_ITEM".equals(relationship.getToEntityType())) referencedItemIds.add(relationship.getToEntityId());
        });
        List<WorkItem> allItems = referencedItemIds.isEmpty() ? List.of() : workItems.findByIds(referencedItemIds);

        return new ProjectSearchResult(allItems, matchedEntries, matchedRelationships);
    }
}
