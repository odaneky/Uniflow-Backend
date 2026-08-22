package com.university.lms.notification.api;

/** Vendor-neutral outbound email contract. */
public interface EmailSender {

    void send(EmailMessage message);

    record EmailMessage(String to, String subject, String body) {}
}
