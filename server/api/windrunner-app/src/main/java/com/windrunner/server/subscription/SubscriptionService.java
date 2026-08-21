package com.windrunner.server.subscription;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.subscription.api.SubscriptionPageResponse;
import com.windrunner.server.subscription.api.SubscriptionView;
import com.windrunner.server.subscription.persistence.SubscriptionRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.work.persistence.WorkItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SubscriptionService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SubscriptionRepository subscriptionRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final EntityIdGenerator idGenerator;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               ProjectAccessService projectAccessService,
                               ProjectRepository projectRepository,
                               WorkItemRepository workItemRepository,
                               EntityIdGenerator idGenerator) {
        this.subscriptionRepository = subscriptionRepository;
        this.projectAccessService = projectAccessService;
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.idGenerator = idGenerator;
    }

    public boolean subscribe(AppUser actor, String projectId, String workItemId) {
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        requireWorkItemInProject(projectId, workItemId);
        subscriptionRepository.insert(
                idGenerator.generate(EntityIdType.WORK_ITEM_SUBSCRIPTION),
                actor.getId(),
                projectId,
                workItemId);
        return true;
    }

    public boolean unsubscribe(AppUser actor, String projectId, String workItemId) {
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        subscriptionRepository.delete(actor.getId(), workItemId);
        return false;
    }

    public boolean isSubscribed(AppUser actor, String projectId, String workItemId) {
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.VIEWER);
        return subscriptionRepository.exists(actor.getId(), workItemId);
    }

    public SubscriptionPageResponse listForUser(AppUser actor, int page, int size) {
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) Math.max(page, 0) * normalizedSize;
        List<String> projectIds = visibleProjectIds(actor);
        if (projectIds.isEmpty()) {
            return SubscriptionPageResponse.builder()
                    .items(List.of())
                    .page(page)
                    .size(normalizedSize)
                    .totalItems(0)
                    .totalPages(0)
                    .build();
        }
        List<SubscriptionView> items = subscriptionRepository.listForUser(actor.getId(), projectIds, normalizedSize, offset)
                .stream()
                .map(this::toView)
                .toList();
        long totalItems = subscriptionRepository.countForUser(actor.getId(), projectIds);
        return SubscriptionPageResponse.builder()
                .items(items)
                .page(page)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
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

    private void requireWorkItemInProject(String projectId, String workItemId) {
        if (!workItemRepository.existsInProject(workItemId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found");
        }
    }

    private SubscriptionView toView(SubscriptionRepository.SubscriptionRow row) {
        return new SubscriptionView(
                row.userId(),
                row.projectId(),
                row.projectName(),
                row.workItemId(),
                row.workItemTitle(),
                row.workItemType(),
                row.parentWorkItemId(),
                row.parentWorkItemTitle(),
                row.subscribedAt());
    }
}