package com.windrunner.server.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public void sendText(String recipient, String subject, String body, String replyTo) {
        if (!properties.isEnabled()) {
            return;
        }
        requireConfiguredSender();
        requireText(recipient, "recipient");
        requireText(subject, "subject");
        requireText(body, "body");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom().trim());
        message.setTo(recipient.trim());
        message.setSubject(subject.trim());
        message.setText(body);
        if (StringUtils.hasText(replyTo)) {
            message.setReplyTo(replyTo.trim());
        }

        mailSender.send(message);
    }

    private void requireConfiguredSender() {
        if (!StringUtils.hasText(properties.getFrom())) {
            throw new IllegalStateException("Mail is enabled but windrunner.mail.from is not configured");
        }
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
