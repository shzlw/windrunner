package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.persistence.WorkItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExternalEntryController {

    private final EntryService entries;
    private final com.windrunner.server.work.persistence.EntryRepository entryRepository;
    private final WorkItemRepository workItems;
    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/work-items/{workItemId}/entries")
    public ApiResponse<List<Entry>> list(@PathVariable("workItemId") String workItemId,
                                         HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_READ);
        String projectId = requireProjectId(workItemId);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        return ApiResponse.success(entries.list(projectId).stream()
                .filter(entry -> workItemId.equals(entry.getWorkItemId()))
                .toList());
    }

    @PostMapping("/work-items/{workItemId}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Entry> create(@PathVariable("workItemId") String workItemId,
                                     @RequestBody Entry entry,
                                     HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE);
        String projectId = requireProjectId(workItemId);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entry is required");
        }
        entry.setWorkItemId(workItemId);
        return ApiResponse.success(entries.create(projectId, entry, actor.getId()));
    }

    @GetMapping("/entries/{id}")
    public ApiResponse<Entry> get(@PathVariable("id") String id,
                                  HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_READ);
        Entry entry = requireEntry(id);
        projectAccessService.requireProjectRole(entry.getProjectId(), actor, ProjectRoles.VIEWER);
        return ApiResponse.success(entry);
    }

    @PutMapping("/entries/{id}")
    public ApiResponse<Entry> update(@PathVariable("id") String id,
                                     @RequestBody Entry entry,
                                     HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE);
        Entry current = requireEntry(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entry is required");
        }
        return ApiResponse.success(entries.update(current.getProjectId(), id, entry, actor.getId()));
    }

    @DeleteMapping("/entries/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id,
                                    HttpServletRequest request) {
        AppUser actor = externalAccessService.requireScope(request, ApiKeyScopes.ENTRIES_WRITE);
        Entry current = requireEntry(id);
        projectAccessService.requireProjectRole(current.getProjectId(), actor, ProjectRoles.EDITOR);
        entries.delete(current.getProjectId(), id, actor.getId());
        return ApiResponse.success();
    }

    private String requireProjectId(String workItemId) {
        return workItems.findById(workItemId)
                .map(item -> item.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
    }

    private Entry requireEntry(String id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
    }
}
