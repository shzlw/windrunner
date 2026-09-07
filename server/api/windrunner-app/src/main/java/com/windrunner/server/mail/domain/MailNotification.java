package com.windrunner.server.mail.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("mail_notification")
public record MailNotification(
        @Id String id, String recipientUserId, String recipientEmail,
        String workItemId, String actorUserId, String body, int attempts) {
}
