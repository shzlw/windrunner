package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.team.TeamRoles;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class IdentityProposalUtils {
    private IdentityProposalUtils() {
    }

    static String requireValue(String value, String label) {
        if (value == null || value.isBlank()) throw createBadRequestException(label + " is required");
        if (!value.equals(value.trim())) throw createBadRequestException(label + " must not contain surrounding whitespace");
        return value;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static Map<String, String> extractFields(ProposalDraft draft, Set<String> allowed) {
        if (draft.fields() == null || draft.fields().isEmpty() || !allowed.containsAll(draft.fields().keySet())) {
            throw createBadRequestException("Unsupported or empty fields");
        }
        if (draft.fields().values().stream().anyMatch(value -> value != null && value.length() > 4000)) {
            throw createBadRequestException("Field values must be 4000 characters or fewer");
        }
        return draft.fields();
    }

    static Map<String, String> createIdentityMap(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put(pairs[index], pairs[index + 1]);
        return result;
    }

    static void putRevision(Map<String, String> values, String key, OffsetDateTime value) {
        if (value != null) values.put(key, value.toString());
    }

    static OffsetDateTime parseTimestamp(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    static String getDisplayName(AppUser user) {
        return user.getDisplayName() == null ? user.getUsername() : user.getDisplayName();
    }

    static String normalizeTeamRole(String role) {
        try {
            return TeamRoles.normalize(requireValue(role, "Role"));
        } catch (IllegalArgumentException exception) {
            throw createBadRequestException(exception.getMessage());
        }
    }

    static String normalizeProjectRole(String role) {
        try {
            return ProjectRoles.normalize(requireValue(role, "Role"));
        } catch (IllegalArgumentException exception) {
            throw createBadRequestException(exception.getMessage());
        }
    }

    static void validateMembershipAction(String action, String oldRole) {
        if ("ADD".equals(action) && oldRole != null) {
            throw createResponseStatusException(HttpStatus.CONFLICT, "Membership already exists; use UPDATE with these exact IDs");
        }
        if (!"ADD".equals(action) && oldRole == null) {
            throw createResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
    }

    static UpdateUserRequest buildUserUpdateRequest(ProposalDraft draft, Map<String, String> values) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername(values.get("username"));
        request.setEmail(values.get("email"));
        request.setDisplayName(values.get("displayName"));
        request.setTitle(values.get("title"));
        request.setBio(values.get("bio"));
        request.setTimezone(values.get("timezone"));
        request.setStatus(values.get("status"));
        if (draft.fields() != null && draft.fields().containsKey("globalRole")) {
            request.setGlobalRole(values.get("globalRole"));
        }
        return request;
    }

    static ResponseStatusException createBadRequestException(String message) {
        return createResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException createResponseStatusException(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
