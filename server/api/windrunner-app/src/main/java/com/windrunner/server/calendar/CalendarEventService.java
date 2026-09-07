package com.windrunner.server.calendar;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.calendar.api.CalendarEventConflictView;
import com.windrunner.server.calendar.api.CalendarEventRequest;
import com.windrunner.server.calendar.api.CalendarEventView;
import com.windrunner.server.calendar.domain.CalendarEvent;
import com.windrunner.server.calendar.persistence.CalendarEventRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.DateUtils;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 20_000;
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    private final CalendarEventRepository calendarEventRepository;
    private final AppUserRepository appUserRepository;
    private final WorkItemRepository workItemRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final EntityIdGenerator idGenerator;

    public List<CalendarEventView> listEvents(AppUser actor,
                                              String requestedUserId,
                                              OffsetDateTime from,
                                              OffsetDateTime to) {
        AppUser target = requireTargetUser(requestedUserId, actor);
        validateRange(from, to);
        return calendarEventRepository.findByUserIdAndRange(target.getId(), from, to)
                .stream()
                .map(CalendarEventView::from)
                .toList();
    }

    public CalendarEventView getEvent(String id, AppUser actor) {
        CalendarEvent event = requireEvent(id);
        requireEventAccess(event, actor);
        return CalendarEventView.from(event);
    }

    @Transactional
    public CalendarEventView createEvent(CalendarEventRequest request, AppUser actor) {
        if (request == null) {
            throw createBadRequestException("Request body is required");
        }
        AppUser target = requireTargetUser(request.userId(), actor);
        CalendarEvent event = normalize(request, target, actor, null);
        event.setId(idGenerator.generate(EntityIdType.CALENDAR_EVENT));
        event.setCreatedAt(DateUtils.now());
        event.setUpdatedAt(event.getCreatedAt());
        int inserted = calendarEventRepository.insert(
                event.getId(), event.getUserId(), event.getEventType(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.getTimezone(), Boolean.TRUE.equals(event.getAllDay()),
                Boolean.TRUE.equals(event.getShowAsBusy()), event.getWorkItemId(), event.getCreatedByUserId(),
                event.getCreatedAt(), event.getUpdatedAt());
        if (inserted != 1) {
            throw new IllegalStateException("Expected one calendar_event row to be inserted but got " + inserted);
        }
        return CalendarEventView.from(event);
    }

    @Transactional
    public CalendarEventView updateEvent(String id, CalendarEventRequest request, AppUser actor) {
        if (request == null) {
            throw createBadRequestException("Request body is required");
        }
        CalendarEvent current = requireEvent(id);
        requireEventAccess(current, actor);
        if (StringUtils.hasText(request.userId()) && !current.getUserId().equals(request.userId().trim())) {
            throw createBadRequestException("Calendar event user cannot be changed");
        }
        AppUser target = requireTargetUser(current.getUserId(), actor);
        CalendarEvent event = normalize(request, target, actor, current);
        event.setId(current.getId());
        event.setCreatedAt(current.getCreatedAt());
        event.setUpdatedAt(DateUtils.now());
        int updated = calendarEventRepository.update(
                event.getId(), event.getUserId(), event.getEventType(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.getTimezone(), Boolean.TRUE.equals(event.getAllDay()),
                Boolean.TRUE.equals(event.getShowAsBusy()), event.getWorkItemId(), event.getUpdatedAt());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar event not found");
        }
        return CalendarEventView.from(event);
    }

    @Transactional
    public void deleteEvent(String id, AppUser actor) {
        CalendarEvent event = requireEvent(id);
        requireEventAccess(event, actor);
        if (calendarEventRepository.delete(event.getId(), event.getUserId()) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar event not found");
        }
    }

    public List<CalendarEventConflictView> listConflicts(String projectId,
                                                          String userId,
                                                          OffsetDateTime from,
                                                          OffsetDateTime to,
                                                          AppUser actor) {
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (!StringUtils.hasText(userId) || !projectMemberRepository.isUserInProject(projectId, userId.trim())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project user not found");
        }
        validateRange(from, to);
        return calendarEventRepository.findBusyByUserIdAndRange(userId.trim(), from, to)
                .stream()
                .map(CalendarEventConflictView::from)
                .toList();
    }

    private CalendarEvent normalize(CalendarEventRequest request,
                                   AppUser target,
                                   AppUser actor,
                                   CalendarEvent current) {
        if (!StringUtils.hasText(request.eventType())) {
            throw createBadRequestException("Event type is required");
        }
        String eventType = request.eventType().trim().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPE_PATTERN.matcher(eventType).matches()) {
            throw createBadRequestException("Event type must contain 1-40 uppercase letters, numbers, or underscores");
        }
        if (!StringUtils.hasText(request.title()) || request.title().trim().length() > MAX_TITLE_LENGTH) {
            throw createBadRequestException("Event title is required and must be at most 200 characters");
        }
        String description = normalizeOptionalText(request.description());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw createBadRequestException("Event description must be at most 20000 characters");
        }
        if (request.startsAt() == null || request.endsAt() == null || !request.endsAt().isAfter(request.startsAt())) {
            throw createBadRequestException("Event end must be after event start");
        }
        String timezone = normalizeTimezone(request.timezone(), target.getTimezone());
        String workItemId = normalizeOptionalText(request.workItemId());
        if (workItemId != null) {
            var workItem = workItemRepository.findById(workItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
            if (!AppRoles.isAdminLike(actor.getGlobalRole())) {
                projectAccessService.requireProjectRole(workItem.getProjectId(), actor, ProjectRoles.VIEWER);
            }
        }

        CalendarEvent event = new CalendarEvent();
        event.setUserId(target.getId());
        event.setEventType(eventType);
        event.setTitle(request.title().trim());
        event.setDescription(description);
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setTimezone(timezone);
        event.setAllDay(request.allDay() == null ? Boolean.FALSE : request.allDay());
        event.setShowAsBusy(request.showAsBusy() == null ? Boolean.TRUE : request.showAsBusy());
        event.setWorkItemId(workItemId);
        event.setCreatedByUserId(current == null ? actor.getId() : current.getCreatedByUserId());
        return event;
    }

    private AppUser requireTargetUser(String requestedUserId, AppUser actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String targetUserId = StringUtils.hasText(requestedUserId) ? requestedUserId.trim() : actor.getId();
        if (!actor.getId().equals(targetUserId) && !AppRoles.isAdminLike(actor.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can manage another user's calendar");
        }
        return appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireEventAccess(CalendarEvent event, AppUser actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!actor.getId().equals(event.getUserId()) && !AppRoles.isAdminLike(actor.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calendar access is required");
        }
    }

    private CalendarEvent requireEvent(String id) {
        if (!StringUtils.hasText(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar event not found");
        }
        return calendarEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar event not found"));
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw createBadRequestException("Calendar range must have an end after its start");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw createBadRequestException("Calendar range cannot exceed 366 days");
        }
    }

    private String normalizeTimezone(String requestedTimezone, String fallbackTimezone) {
        String timezone = StringUtils.hasText(requestedTimezone) ? requestedTimezone.trim() : fallbackTimezone;
        if (!StringUtils.hasText(timezone)) {
            timezone = "UTC";
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw createBadRequestException("Timezone must be a valid IANA timezone");
        }
        return timezone;
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResponseStatusException createBadRequestException(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
