package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.domain.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchEntriesTool implements Tool<FetchEntriesTool.Parameters> {
    private final EntryService entries;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_entries";
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-entries-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(
                context, parameters == null ? null : parameters.projectId());
        String workItemId = parameters.workItemId() == null ? null : parameters.workItemId().trim();
        int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        long offset = parameters.offset() == null ? 0 : Math.max(0, parameters.offset());
        List<Entry> results = entries.listPageForTool(projectId, workItemId, limit, offset);
        long total = entries.countForTool(projectId, workItemId);
        List<Entry> bounded = results.stream().map(this::bounded).toList();
        return new Response(bounded, bounded.size(), total, limit, offset, offset + results.size() < total);
    }

    private Entry bounded(Entry entry) {
        Entry copy = new Entry();
        copy.setId(entry.getId());
        copy.setProjectId(entry.getProjectId());
        copy.setWorkItemId(entry.getWorkItemId());
        copy.setSortIndex(entry.getSortIndex());
        copy.setAuthorUserId(entry.getAuthorUserId());
        copy.setAuthorDisplayName(entry.getAuthorDisplayName());
        copy.setType(entry.getType());
        copy.setBody(AiReviewLimits.bounded(entry.getBody(), AiReviewLimits.MAX_TEXT_LENGTH));
        copy.setCreatedAt(entry.getCreatedAt());
        copy.setUpdatedAt(entry.getUpdatedAt());
        return copy;
    }

    public record Parameters(String projectId, String workItemId, Integer limit, Integer offset) {
    }

    public record Response(List<Entry> entries, int count, long total, int limit, long offset, boolean hasMore) {
    }
}
