package com.university.lms.request.domain;

public enum ServiceRequestStatus {
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    COMPLETED,
    DENIED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == DENIED || this == CANCELLED;
    }
}
