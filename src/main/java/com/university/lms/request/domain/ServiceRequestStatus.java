package com.university.lms.request.domain;

public enum ServiceRequestStatus {
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    COMPLETED,
    DENIED;

    public boolean terminal() {
        return this == COMPLETED || this == DENIED;
    }
}
