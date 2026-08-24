package com.university.lms.notification.dispatch;

import com.university.lms.notification.api.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Logs outbound email — default adapter for local development.
 *
 * <p>Query strings are stripped before logging. Some messages carry a capability token in a link —
 * the applicant's resume link, for one — and a development adapter that wrote it to the log would
 * put the credential in exactly the place the token design exists to keep it out of. Truncation is
 * not a safeguard: it hid the token here only because the link happened to fall past the cut.
 */
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
                truncate(redactQueryStrings(message.body()), 120));
    }

    /** Replaces everything after a {@code ?} in a URL, which is where secrets ride. */
    private static String redactQueryStrings(String body) {
        return body == null ? null : QUERY_STRING.matcher(body).replaceAll("$1?[redacted]");
    }

    private static final java.util.regex.Pattern QUERY_STRING =
            java.util.regex.Pattern.compile("(https?://[^\\s?]+)\\?\\S*");

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
