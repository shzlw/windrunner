package com.windrunner.server.notification;

import com.windrunner.server.subscription.persistence.SubscriptionRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the notification audience for a work item: subscribers plus
 * effective assignees (direct users and members of assigned teams),
 * excluding the acting user.
 */
@Component
@RequiredArgsConstructor
public class WorkItemNotificationAudience {

    private final SubscriptionRepository subscriptions;
    private final WorkItemAssigneeRepository assignees;
    private final TeamMemberRepository teamMembers;

    public Set<String> resolve(String workItemId, String excludeUserId) {
        Set<String> audience = new LinkedHashSet<>();
        audience.addAll(subscriptions.findUserIdsByWorkItemId(workItemId));
        List<WorkItemAssignee> assigned = assignees.findByWorkItemId(workItemId);
        Set<String> teamIds = new LinkedHashSet<>();
        for (WorkItemAssignee assignee : assigned) {
            if ("USER".equalsIgnoreCase(assignee.getAssigneeType())) {
                audience.add(assignee.getAssigneeId());
            } else if ("TEAM".equalsIgnoreCase(assignee.getAssigneeType())) {
                teamIds.add(assignee.getAssigneeId());
            }
        }
        if (!teamIds.isEmpty()) {
            teamMembers.findByTeamIds(new ArrayList<>(teamIds)).forEach(member -> audience.add(member.getUserId()));
        }
        if (excludeUserId != null) {
            audience.remove(excludeUserId);
        }
        return audience;
    }
}
