package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.domain.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchEntriesTool implements Tool<FetchEntriesTool.Parameters> {
    private final EntryService entries;

    @Override
    public String name() {
        return "fetch_entries";
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
    public Object execute(Parameters parameters) {
        if (parameters == null || parameters.projectId() == null || parameters.projectId().isBlank())
            throw new IllegalArgumentException("projectId is required");
        String workItemId = parameters.workItemId() == null ? null : parameters.workItemId().trim();
        int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        long offset = parameters.offset() == null ? 0 : Math.max(0, parameters.offset());
        List<Entry> results = entries.listPageForTool(parameters.projectId().trim(), workItemId, limit, offset);
        long total = entries.countForTool(parameters.projectId().trim(), workItemId);
        return new Response(results, results.size(), total, limit, offset, offset + results.size() < total);
    }

    public record Parameters(String projectId, String workItemId, Integer limit, Integer offset) {
    }

    public record Response(List<Entry> entries, int count, long total, int limit, long offset, boolean hasMore) {
    }
}
