package com.windrunner.server.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class MailServiceTest {

    @Test
    void doesNotSendWhenMailIsDisabled() {
        RecordingMailSender mailSender = new RecordingMailSender();
        MailProperties properties = new MailProperties();
        MailService service = new MailService(mailSender, properties);

        service.sendText("recipient@example.com", "Subject", "Body", null);

        assertThat(mailSender.getSentMessage()).isNull();
    }

    @Test
    void sendsConfiguredTextMessage() {
        RecordingMailSender mailSender = new RecordingMailSender();
        MailProperties properties = new MailProperties();
        properties.setEnabled(true);
        properties.setFrom(" sender@example.com ");
        MailService service = new MailService(mailSender, properties);

        service.sendText(" recipient@example.com ", " Subject ", "Body", "reply@example.com");

        SimpleMailMessage message = mailSender.getSentMessage();
        assertThat(message.getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("recipient@example.com");
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getText()).isEqualTo("Body");
        assertThat(message.getReplyTo()).isEqualTo("reply@example.com");
    }

    private static final class RecordingMailSender extends JavaMailSenderImpl {

        private SimpleMailMessage sentMessage;

        @Override
        public void send(SimpleMailMessage message) {
            sentMessage = message;
        }

        private SimpleMailMessage getSentMessage() {
            return sentMessage;
        }
    }
}
