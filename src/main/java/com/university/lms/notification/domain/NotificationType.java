package com.university.lms.notification.domain;

/** What a notification is about; drives templating and user-level preferences. */
public enum NotificationType {
    ENROLMENT,
    ASSESSMENT_DUE,
    GRADE_PUBLISHED,
    ANNOUNCEMENT,
    MESSAGE,
    SYSTEM
}
