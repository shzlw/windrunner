package com.windrunner.server.tools.calendar;

import com.windrunner.server.calendar.CalendarEventService;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class FetchTeamCalendarWorkloadTool implements Tool<FetchTeamCalendarWorkloadTool.Parameters> {

    private final CalendarEventService calendarEventService;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_team_calendar_workload";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-team-calendar-workload-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        var actor = authorization.requireActor(context);
        return calendarEventService.getTeamWorkload(
                actor,
                parameters == null ? null : parameters.teamId(),
                parameters == null ? null : parameters.from(),
                parameters == null ? null : parameters.to());
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    public record Parameters(String teamId, LocalDate from, LocalDate to) {
    }
}
