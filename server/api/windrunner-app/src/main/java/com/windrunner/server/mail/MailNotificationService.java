package com.windrunner.server.mail;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.mail.persistence.MailNotificationRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailNotificationService {
    private final MailProperties properties;
    private final MailNotificationRepository queue;
    private final AppUserRepository users;
    private final EntityIdGenerator ids;

    @Transactional
    public void queueAssignments(Collection<String> userIds, String actorId, String workItemId) {
        if (!properties.isEnabled()) return;
        userIds.stream().distinct().filter(id -> !Objects.equals(id, actorId)).forEach(id ->
                users.findById(id).filter(user -> "ACTIVE".equals(user.getStatus()))
                        .filter(user -> StringUtils.hasText(user.getEmail())).ifPresent(user ->
                                queue.insert(ids.generate(EntityIdType.MAIL_NOTIFICATION), id,
                                        user.getEmail(), workItemId, actorId, null, 120)));
    }

    @Transactional
    public void queueAccountChanges(Map<String, Object> before, AppUser after) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(before.get("globalRole"), after.getGlobalRole()))
            changes.add("Your account role changed to " + after.getGlobalRole() + ".");
        if (!Objects.equals(before.get("status"), after.getStatus()))
            changes.add("Your account status changed to " + after.getStatus() + ".");
        boolean emailChanged = !Objects.equals(before.get("email"), after.getEmail());
        if (emailChanged) changes.add("Your account email address was changed.");
        if (changes.isEmpty()) return;
        // An address change is reported to the previous address, never to an unverified replacement.
        queueAccountNotice(after.getId(), emailChanged ? (String) before.get("email") : after.getEmail(),
                String.join("\n", changes));
    }

    @Transactional
    public void queueAccountNotice(String userId, String email, String body) {
        if (!properties.isEnabled() || !StringUtils.hasText(email)) return;
        queue.insert(ids.generate(EntityIdType.MAIL_NOTIFICATION), userId, email,
                null, null, body + "\n\nIf you did not expect this change, contact your administrator.", 0);
    }
}
