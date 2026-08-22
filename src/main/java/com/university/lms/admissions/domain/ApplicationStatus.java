package com.university.lms.admissions.domain;

public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    ADMITTED,
    DENIED,
    WAITLISTED,
    MATRICULATED;

    public boolean terminal() {
        return this == DENIED || this == MATRICULATED;
    }

    public boolean staffQueue() {
        return this == SUBMITTED || this == IN_REVIEW || this == WAITLISTED || this == ADMITTED;
    }
}
