package com.windrunner.server.calendar.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.calendar.CalendarEventService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/calendar")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;
    private final AuthService authService;

    @GetMapping("/events")
    public ApiResponse<List<CalendarEventView>> listEvents(
            @RequestParam(name = "from") OffsetDateTime from,
            @RequestParam(name = "to") OffsetDateTime to,
            @RequestParam(name = "userId", required = false) String userId,
            HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(calendarEventService.listEvents(actor, userId, from, to));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<CalendarEventView> getEvent(@PathVariable("id") String id,
                                                    HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(calendarEventService.getEvent(id, actor));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CalendarEventView> createEvent(@RequestBody CalendarEventRequest body,
                                                       HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(calendarEventService.createEvent(body, actor));
    }

    @PutMapping("/events/{id}")
    public ApiResponse<CalendarEventView> updateEvent(@PathVariable("id") String id,
                                                       @RequestBody CalendarEventRequest body,
                                                       HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(calendarEventService.updateEvent(id, body, actor));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> deleteEvent(@PathVariable("id") String id,
                                          HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        calendarEventService.deleteEvent(id, actor);
        return ApiResponse.success();
    }

    @GetMapping("/conflicts")
    public ApiResponse<List<CalendarEventConflictView>> listConflicts(
            @RequestParam("projectId") String projectId,
            @RequestParam("userId") String userId,
            @RequestParam("from") OffsetDateTime from,
            @RequestParam("to") OffsetDateTime to,
            HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(calendarEventService.listConflicts(projectId, userId, from, to, actor));
    }
}
