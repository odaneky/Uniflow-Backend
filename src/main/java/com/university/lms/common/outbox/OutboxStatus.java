package com.university.lms.common.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
