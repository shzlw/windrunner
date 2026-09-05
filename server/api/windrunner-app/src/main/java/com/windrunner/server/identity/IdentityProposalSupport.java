package com.windrunner.server.identity;

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

final class IdentityProposalSupport {
    private IdentityProposalSupport() {
    }

    static String required(String value, String label) {
        if (value == null || value.isBlank()) throw bad(label + " is required");
        if (!value.equals(value.trim())) throw bad(label + " must not contain surrounding whitespace");
        return value;
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    static Map<String, String> fields(IdentityProposalService.Draft draft, Set<String> allowed) {
        if (draft.fields() == null || draft.fields().isEmpty() || !allowed.containsAll(draft.fields().keySet())) {
            throw bad("Unsupported or empty fields");
        }
        if (draft.fields().values().stream().anyMatch(value -> value != null && value.length() > 4000)) {
            throw bad("Field values must be 4000 characters or fewer");
        }
        return draft.fields();
    }

    static Map<String, String> identity(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put(pairs[index], pairs[index + 1]);
        return result;
    }

    static void putRevision(Map<String, String> values, String key, OffsetDateTime value) {
        if (value != null) values.put(key, value.toString());
    }

    static OffsetDateTime timestamp(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    static String display(AppUser user) {
        return user.getDisplayName() == null ? user.getUsername() : user.getDisplayName();
    }

    static String teamRole(String role) {
        try {
            return TeamRoles.normalize(required(role, "Role"));
        } catch (IllegalArgumentException exception) {
            throw bad(exception.getMessage());
        }
    }

    static String projectRole(String role) {
        try {
            return ProjectRoles.normalize(required(role, "Role"));
        } catch (IllegalArgumentException exception) {
            throw bad(exception.getMessage());
        }
    }

    static void membershipAction(String action, String oldRole) {
        if ("ADD".equals(action) && oldRole != null) {
            throw error(HttpStatus.CONFLICT, "Membership already exists; use UPDATE with these exact IDs");
        }
        if (!"ADD".equals(action) && oldRole == null) {
            throw error(HttpStatus.NOT_FOUND, "Membership not found");
        }
    }

    static UpdateUserRequest userRequest(IdentityProposalService.Draft draft, Map<String, String> values) {
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

    static ResponseStatusException bad(String message) {
        return error(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
