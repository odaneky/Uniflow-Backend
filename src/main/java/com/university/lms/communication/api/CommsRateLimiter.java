package com.university.lms.communication.api;

import java.util.UUID;

/** Guards comms write endpoints against abuse. */
public interface CommsRateLimiter {

    /** Throws {@link com.university.lms.common.exception.RateLimitExceededException} when over limit. */
    void check(String bucket, UUID userId);
}
