package com.windrunner.server.work;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.api.AssignedWorkItemView;
import com.windrunner.server.work.persistence.WorkItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignedWorkService {

    private static final int MAX_PAGE_SIZE = 50;

    private final WorkItemRepository workItems;
    private final ProjectRepository projectRepository;

    public List<AssignedWorkItemView> listAssignedToUser(AppUser actor, int page, int size) {
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) Math.max(page, 0) * normalizedSize;
        List<String> projectIds = visibleProjectIds(actor);
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return workItems.listAssignedToUser(actor.getId(), projectIds, normalizedSize, offset)
                .stream()
                .map(row -> new AssignedWorkItemView(
                        row.projectId(),
                        row.projectName(),
                        row.workItemId(),
                        row.title(),
                        row.type(),
                        row.status(),
                        row.dueDate(),
                        row.priority(),
                        row.updatedAt()))
                .toList();
    }

    public long countAssignedToUser(AppUser actor) {
        List<String> projectIds = visibleProjectIds(actor);
        if (projectIds.isEmpty()) {
            return 0;
        }
        return workItems.countAssignedToUser(actor.getId(), projectIds);
    }

    private List<String> visibleProjectIds(AppUser actor) {
        if (AppRoles.isSuperAdmin(actor.getGlobalRole())) {
            return projectRepository.findAllByOrderByNameAscIdAsc().stream()
                    .map(Project::getId)
                    .toList();
        }
        return projectRepository.findVisibleToUser(actor.getId()).stream()
                .map(Project::getId)
                .toList();
    }
}
