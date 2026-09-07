package com.windrunner.server.mail;

import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties properties;

    @PostConstruct
    public void validateConfiguration() {
        if (!properties.isEnabled()) return;
        requireConfiguredSender();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Mail is enabled but SMTP is not configured");
        if (sender instanceof JavaMailSenderImpl smtp) {
            // Bound the background worker's network waits; explicit SMTP settings take precedence.
            smtp.getJavaMailProperties().putIfAbsent("mail.smtp.connectiontimeout", "5000");
            smtp.getJavaMailProperties().putIfAbsent("mail.smtp.timeout", "5000");
            smtp.getJavaMailProperties().putIfAbsent("mail.smtp.writetimeout", "5000");
        }
    }

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

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }
        sender.send(message);
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
