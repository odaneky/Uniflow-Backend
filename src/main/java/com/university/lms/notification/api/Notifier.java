package com.university.lms.notification.api;

import com.university.lms.notification.domain.NotificationType;
import java.util.UUID;

/**
 * Tells a person something, on the system's initiative.
 *
 * <p>Distinct from the staff-facing notification endpoint, which requires a signed-in member of
 * staff because a person is composing a message. These are raised by the application itself — an
 * exam moved, a decision published — and must work regardless of who happened to trigger them, or
 * whether anybody did.
 *
 * <p>Delivery never fails the operation that caused it. Refusing to move an exam because a
 * notification could not be written would be the wrong trade: the move is the fact, the message is
 * about the fact.
 */
public interface Notifier {

    void notifyUser(UUID recipientUserId, NotificationType type, String title, String body, String actionUrl);
}
