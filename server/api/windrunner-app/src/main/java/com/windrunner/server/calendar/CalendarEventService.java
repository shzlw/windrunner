package com.windrunner.server.calendar;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.calendar.api.CalendarEventConflictView;
import com.windrunner.server.calendar.api.CalendarEventRequest;
import com.windrunner.server.calendar.api.CalendarEventView;
import com.windrunner.server.calendar.api.CalendarScopeMemberView;
import com.windrunner.server.calendar.api.CalendarScopeView;
import com.windrunner.server.calendar.api.CalendarWorkItemView;
import com.windrunner.server.calendar.domain.CalendarEvent;
import com.windrunner.server.calendar.persistence.CalendarEventRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 20_000;
    private static final int MAX_WORK_ITEM_RESULTS = 500;

    private final CalendarEventRepository calendarEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;
    private final WorkItemRepository workItemRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EntityIdGenerator idGenerator;

    public List<CalendarScopeView> listScopes(AppUser actor) {
        requireAuthenticatedActor(actor);
        List<String> teamIds = teamMemberRepository.findByUserId(actor.getId()).stream()
                .map(TeamMember::getTeamId)
                .distinct()
                .toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }

        Map<String, Team> teamsById = new LinkedHashMap<>();
        teamRepository.findAllById(teamIds).forEach(team -> teamsById.put(team.getId(), team));
        List<TeamMember> members = teamMemberRepository.findByTeamIds(teamIds);
        Map<String, List<TeamMember>> membersByTeamId = new LinkedHashMap<>();
        members.forEach(member -> membersByTeamId.computeIfAbsent(member.getTeamId(), ignored -> new ArrayList<>()).add(member));
        Map<String, String> displayNamesByUserId = new LinkedHashMap<>();
        appUserRepository.findAllById(members.stream().map(TeamMember::getUserId).distinct().toList())
                .forEach(user -> displayNamesByUserId.put(user.getId(), getDisplayName(user)));

        return teamIds.stream()
                .map(teamsById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Team::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(team -> new CalendarScopeView(
                        team.getId(),
                        team.getName(),
                        membersByTeamId.getOrDefault(team.getId(), List.of()).stream()
                                .map(member -> new CalendarScopeMemberView(
                                        member.getUserId(),
                                        displayNamesByUserId.getOrDefault(member.getUserId(), "Unknown user")))
                                .toList()))
                .toList();
    }

    public List<CalendarEventView> listEvents(AppUser actor,
                                              String scopeType,
                                              String scopeId,
                                              OffsetDateTime from,
                                              OffsetDateTime to) {
        List<String> userIds = resolveScopeUserIds(actor, scopeType, scopeId);
        validateRange(from, to);
        if (userIds.isEmpty()) {
            return List.of();
        }
        return calendarEventRepository.findByUserIdsAndRange(userIds, from, to)
                .stream()
                .map(CalendarEventView::from)
                .toList();
    }

    public List<CalendarWorkItemView> listWorkItems(AppUser actor,
                                                    String scopeType,
                                                    String scopeId,
                                                    OffsetDateTime from,
        OffsetDateTime to) {
        validateRange(from, to);
        requireAuthenticatedActor(actor);
        String normalizedScopeType = normalizeScopeType(scopeType);
        List<WorkItemRepository.CalendarAssignmentRow> rows;
        if ("USER".equals(normalizedScopeType)) {
            AppUser target = requireReadableTargetUser(scopeId, actor);
            List<String> projectIds = findVisibleProjectIds(actor);
            if (projectIds.isEmpty()) {
                return List.of();
            }
            rows = workItemRepository.findCalendarAssignmentsByUserIds(
                    projectIds, List.of(target.getId()), MAX_WORK_ITEM_RESULTS);
        } else {
            Team team = requireReadableTeam(scopeId, actor);
            List<String> memberIds = teamMemberRepository.findByTeamId(team.getId()).stream()
                    .map(TeamMember::getUserId)
                    .distinct()
                    .toList();
            if (memberIds.isEmpty()) {
                return List.of();
            }
            List<String> projectIds = findVisibleProjectIds(actor);
            if (projectIds.isEmpty()) {
                return List.of();
            }
            rows = workItemRepository.findCalendarAssignmentsForTeam(
                    projectIds, memberIds, team.getId(), MAX_WORK_ITEM_RESULTS);
        }

        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, LocalDate> startedOnByWorkItemId = findLatestStartedDates(rows, actor);
        LocalDate fromDate = from.toLocalDate();
        LocalDate toDate = to.toLocalDate();
        LocalDate today = LocalDate.now(resolveZone(actor.getTimezone()));
        Map<String, CalendarWorkItemView> workItemsByAssignment = new LinkedHashMap<>();
        for (WorkItemRepository.CalendarAssignmentRow row : rows) {
            LocalDate startedOn = startedOnByWorkItemId.get(row.workItemId());
            boolean overdue = row.dueDate() != null && row.dueDate().isBefore(today);
            CalendarWorkItemView view = new CalendarWorkItemView(
                    row.workItemId(),
                    row.projectId(),
                    row.projectName(),
                    row.title(),
                    row.status(),
                    row.assigneeType(),
                    row.assigneeId(),
                    startedOn,
                    row.dueDate(),
                    overdue);
            if (startedOn == null || overlapsCalendarRange(view, fromDate, toDate, today)) {
                String key = row.workItemId() + ":" + row.assigneeType() + ":" + row.assigneeId();
                workItemsByAssignment.put(key, view);
            }
        }
        return List.copyOf(workItemsByAssignment.values());
    }

    public CalendarEventView getEvent(String id, AppUser actor) {
        CalendarEvent event = requireEvent(id);
        requireEventReadAccess(event, actor);
        return CalendarEventView.from(event);
    }

    @Transactional
    public CalendarEventView createEvent(CalendarEventRequest request, AppUser actor) {
        if (request == null) {
            throw createBadRequestException("Request body is required");
        }
        AppUser target = requireTargetUserForWrite(request.userId(), actor);
        CalendarEvent event = normalize(request, target, actor, null);
        event.setId(idGenerator.generate(EntityIdType.CALENDAR_EVENT));
        event.setCreatedAt(DateUtils.now());
        event.setUpdatedAt(event.getCreatedAt());
        int inserted = calendarEventRepository.insert(
                event.getId(), event.getUserId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.getTimezone(), Boolean.TRUE.equals(event.getAllDay()),
                event.getWorkItemId(), event.getCreatedByUserId(), event.getCreatedAt(), event.getUpdatedAt());
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
        requireEventWriteAccess(current, actor);
        if (StringUtils.hasText(request.userId()) && !current.getUserId().equals(request.userId().trim())) {
            throw createBadRequestException("Calendar event user cannot be changed");
        }
        AppUser target = requireTargetUserForWrite(current.getUserId(), actor);
        CalendarEvent event = normalize(request, target, actor, current);
        event.setId(current.getId());
        event.setCreatedAt(current.getCreatedAt());
        event.setUpdatedAt(DateUtils.now());
        int updated = calendarEventRepository.update(
                event.getId(), event.getUserId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.getTimezone(), Boolean.TRUE.equals(event.getAllDay()),
                event.getWorkItemId(), event.getUpdatedAt());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar event not found");
        }
        return CalendarEventView.from(event);
    }

    @Transactional
    public void deleteEvent(String id, AppUser actor) {
        CalendarEvent event = requireEvent(id);
        requireEventWriteAccess(event, actor);
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
        return calendarEventRepository.findByUserIdAndRange(userId.trim(), from, to)
                .stream()
                .map(CalendarEventConflictView::from)
                .toList();
    }

    private CalendarEvent normalize(CalendarEventRequest request,
                                   AppUser target,
                                   AppUser actor,
                                   CalendarEvent current) {
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
        event.setTitle(request.title().trim());
        event.setDescription(description);
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setTimezone(timezone);
        event.setAllDay(request.allDay() == null ? Boolean.FALSE : request.allDay());
        event.setWorkItemId(workItemId);
        event.setCreatedByUserId(current == null ? actor.getId() : current.getCreatedByUserId());
        return event;
    }

    private List<String> resolveScopeUserIds(AppUser actor, String scopeType, String scopeId) {
        requireAuthenticatedActor(actor);
        String normalizedScopeType = normalizeScopeType(scopeType);
        if ("USER".equals(normalizedScopeType)) {
            AppUser target = requireReadableTargetUser(scopeId, actor);
            return List.of(target.getId());
        }
        if ("TEAM".equals(normalizedScopeType)) {
            if (!StringUtils.hasText(scopeId)) {
                throw createBadRequestException("Team scope id is required");
            }
            Team team = teamRepository.findById(scopeId.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
            requireTeamReadAccess(team.getId(), actor);
            return teamMemberRepository.findByTeamId(team.getId()).stream()
                    .map(TeamMember::getUserId)
                    .distinct()
                    .toList();
        }
        throw createBadRequestException("Calendar scope type must be USER or TEAM");
    }

    private String normalizeScopeType(String scopeType) {
        String normalizedScopeType = StringUtils.hasText(scopeType) ? scopeType.trim().toUpperCase(Locale.ROOT) : "USER";
        if (!"USER".equals(normalizedScopeType) && !"TEAM".equals(normalizedScopeType)) {
            throw createBadRequestException("Calendar scope type must be USER or TEAM");
        }
        return normalizedScopeType;
    }

    private Team requireReadableTeam(String requestedTeamId, AppUser actor) {
        if (!StringUtils.hasText(requestedTeamId)) {
            throw createBadRequestException("Team scope id is required");
        }
        Team team = teamRepository.findById(requestedTeamId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        requireTeamReadAccess(team.getId(), actor);
        return team;
    }

    private List<String> findVisibleProjectIds(AppUser actor) {
        if (AppRoles.isAdminLike(actor.getGlobalRole())) {
            return projectRepository.findAllByOrderByNameAscIdAsc().stream()
                    .map(Project::getId)
                    .toList();
        }
        return projectRepository.findVisibleToUser(actor.getId()).stream()
                .map(Project::getId)
                .toList();
    }

    private Map<String, LocalDate> findLatestStartedDates(
            List<WorkItemRepository.CalendarAssignmentRow> rows,
            AppUser actor) {
        List<String> workItemIds = rows.stream()
                .map(WorkItemRepository.CalendarAssignmentRow::workItemId)
                .distinct()
                .toList();
        Map<String, LocalDate> startedOnByWorkItemId = new HashMap<>();
        ZoneId zone = resolveZone(actor.getTimezone());
        auditLogRepository.findWorkItemStatusHistory(workItemIds).forEach(statusRow -> {
            if ("IN_PROGRESS".equalsIgnoreCase(statusRow.status()) && statusRow.occurredAt() != null) {
                startedOnByWorkItemId.put(statusRow.workItemId(), statusRow.occurredAt().atZoneSameInstant(zone).toLocalDate());
            }
        });
        return startedOnByWorkItemId;
    }

    private boolean overlapsCalendarRange(CalendarWorkItemView workItem,
                                          LocalDate from,
                                          LocalDate to,
                                          LocalDate today) {
        LocalDate start = workItem.startedOn();
        if (start == null) {
            return false;
        }
        LocalDate end = workItem.dueDate();
        if (end == null || end.isBefore(start)) {
            end = to;
        } else if (end.isBefore(today)) {
            end = today;
        }
        return !start.isAfter(to) && !end.isBefore(from);
    }

    private ZoneId resolveZone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            return ZoneId.of("UTC");
        }
    }

    private AppUser requireTargetUserForWrite(String requestedUserId, AppUser actor) {
        requireAuthenticatedActor(actor);
        String targetUserId = StringUtils.hasText(requestedUserId) ? requestedUserId.trim() : actor.getId();
        if (!actor.getId().equals(targetUserId) && !AppRoles.isAdminLike(actor.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can manage another user's calendar");
        }
        return appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AppUser requireReadableTargetUser(String requestedUserId, AppUser actor) {
        String targetUserId = StringUtils.hasText(requestedUserId) ? requestedUserId.trim() : actor.getId();
        AppUser target = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!actor.getId().equals(target.getId())
                && !AppRoles.isAdminLike(actor.getGlobalRole())
                && !teamMemberRepository.areUsersInSharedTeam(actor.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calendar access is required");
        }
        return target;
    }

    private void requireTeamReadAccess(String teamId, AppUser actor) {
        if (!AppRoles.isAdminLike(actor.getGlobalRole())
                && teamMemberRepository.findByTeamIdAndUserId(teamId, actor.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team calendar access is required");
        }
    }

    private void requireEventReadAccess(CalendarEvent event, AppUser actor) {
        requireAuthenticatedActor(actor);
        if (!actor.getId().equals(event.getUserId())
                && !AppRoles.isAdminLike(actor.getGlobalRole())
                && !teamMemberRepository.areUsersInSharedTeam(actor.getId(), event.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calendar access is required");
        }
    }

    private void requireEventWriteAccess(CalendarEvent event, AppUser actor) {
        requireAuthenticatedActor(actor);
        if (!actor.getId().equals(event.getUserId()) && !AppRoles.isAdminLike(actor.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calendar access is required");
        }
    }

    private AppUser requireAuthenticatedActor(AppUser actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return actor;
    }

    private String getDisplayName(AppUser user) {
        return StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName().trim() : user.getUsername();
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
