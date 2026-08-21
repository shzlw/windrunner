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
        String workItemId = parameters.workItemId();
        int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        List<Entry> results = entries.list(parameters.projectId()).stream().filter(entry -> workItemId == null || workItemId.isBlank() || workItemId.equals(entry.getWorkItemId())).limit(limit).toList();
        return new Response(results, results.size(), limit);
    }

    public record Parameters(String projectId, String workItemId, Integer limit) {
    }

    public record Response(List<Entry> entries, int count, int limit) {
    }
}
