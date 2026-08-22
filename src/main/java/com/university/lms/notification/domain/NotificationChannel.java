package com.university.lms.notification.domain;

/** Delivery route. Only {@link #IN_APP} is served synchronously; the rest require a dispatcher. */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    PUSH
}
