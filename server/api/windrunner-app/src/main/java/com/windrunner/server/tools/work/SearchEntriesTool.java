package com.windrunner.server.tools.work;

import com.windrunner.server.search.SearchNormalizer;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.persistence.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchEntriesTool implements Tool<SearchEntriesTool.Parameters> {

    private final EntryRepository entries;
    private final SearchNormalizer searchNormalizer;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "search_entries";
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("search-entries-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(
                context, parameters == null ? null : parameters.projectId());
        String workItemId = normalize(parameters == null ? null : parameters.workItemId());
        String query = normalize(parameters == null ? null : parameters.query());
        if (query == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        boolean exact = parameters != null && Boolean.TRUE.equals(parameters.exact());
        if (exact && workItemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "workItemId is required for exact entry searches");
        }
        int limit = parameters == null || parameters.limit() == null
                ? 20 : Math.max(1, Math.min(parameters.limit(), 100));
        long offset = parameters == null || parameters.offset() == null
                ? 0 : Math.max(0, parameters.offset());

        List<Entry> results;
        long total;
        if (exact) {
            results = entries.findExactPageByProjectAndWorkItemId(
                    projectId, workItemId, query, limit, offset);
            total = entries.countExactByProjectAndWorkItemId(projectId, workItemId, query);
        } else {
            String ftsQuery = searchNormalizer.normalize(query);
            if (ftsQuery.isBlank()) {
                return new Response(List.of(), 0, 0, limit, offset, false, false);
            }
            results = entries.searchPageInProject(
                    projectId, workItemId, ftsQuery, query, limit, offset);
            total = entries.countSearchInProject(projectId, workItemId, ftsQuery, query);
        }
        List<EntryResult> boundedResults = results.stream().map(EntryResult::from).toList();
        return new Response(boundedResults, boundedResults.size(), total, limit, offset,
                offset + results.size() < total, exact);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record Parameters(String projectId, String workItemId, String query, Boolean exact,
                             Integer limit, Integer offset) {
        public Parameters(String projectId, String workItemId, String query, Integer limit, Integer offset) {
            this(projectId, workItemId, query, false, limit, offset);
        }

        public Parameters(String projectId, String workItemId, String query, Integer limit) {
            this(projectId, workItemId, query, false, limit, null);
        }
    }

    public record Response(List<EntryResult> entries, int count, long total, int limit, long offset,
                           boolean hasMore, boolean exact) {
    }

    public record EntryResult(String id, String projectId, String workItemId, Integer sortIndex,
                              String authorUserId, String type, String body,
                              java.time.OffsetDateTime createdAt, java.time.OffsetDateTime updatedAt) {
        static EntryResult from(Entry entry) {
            return new EntryResult(entry.getId(), entry.getProjectId(), entry.getWorkItemId(), entry.getSortIndex(),
                    entry.getAuthorUserId(), entry.getType(),
                    AiReviewLimits.bounded(entry.getBody(), AiReviewLimits.MAX_TEXT_LENGTH),
                    entry.getCreatedAt(), entry.getUpdatedAt());
        }
    }
}
