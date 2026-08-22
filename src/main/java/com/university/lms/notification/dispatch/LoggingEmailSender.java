package com.university.lms.notification.dispatch;

import com.university.lms.notification.api.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Logs outbound email — default adapter for local development. */
@Component
@ConditionalOnProperty(name = "lms.notifications.email.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final String fromAddress;

    public LoggingEmailSender(@Value("${lms.notifications.email.from-address:noreply@uniflow.local}") String fromAddress) {
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        log.info(
                "Email [{} -> {}] subject={} body={}",
                fromAddress,
                message.to(),
                message.subject(),
                truncate(message.body(), 120));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
