package com.university.lms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Client exceeded a configured comms rate limit. */
@Getter
public class RateLimitExceededException extends ApplicationException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                CommonErrorCode.RATE_LIMIT_EXCEEDED,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
