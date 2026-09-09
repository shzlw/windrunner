package com.windrunner.server.tools.work;

import com.windrunner.server.audit.WorkItemTimelineService;
import com.windrunner.server.audit.api.WorkItemTimelineEvent;
import com.windrunner.server.audit.api.WorkItemTimelineSummaryView;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.WorkItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchWorkItemTimelineTool implements Tool<FetchWorkItemTimelineTool.Parameters> {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 10;

    private final WorkItemTimelineService timelineService;
    private final WorkItemService workItemService;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_work_item_timeline";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-work-item-timeline-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(context, parameters == null ? null : parameters.projectId());
        String workItemId = parameters == null || parameters.workItemId() == null ? "" : parameters.workItemId().trim();
        var workItem = workItemService.get(projectId, workItemId);
        int limit = parameters == null || parameters.limit() == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(parameters.limit(), MAX_LIMIT));
        List<WorkItemTimelineEvent> events = timelineService.listLatest(projectId, workItemId, limit);
        return new WorkItemTimelineSummaryView(projectId, workItemId, workItem.getTitle(), events.size(), events);
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    public record Parameters(String projectId, String workItemId, Integer limit) {
    }
}
