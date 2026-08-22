package com.university.lms.communication.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.common.exception.RateLimitExceededException;
import com.university.lms.common.telemetry.CommsMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommsRateLimiterTest {

    private InMemoryCommsRateLimiter rateLimiter;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rateLimiter = new InMemoryCommsRateLimiter(new CommsMetrics(new SimpleMeterRegistry()), 2, 60, 2, 60, 2, 60, 2, 60);
    }

    @Test
    void allowsRequestsUnderLimit() {
        assertThatCode(() -> rateLimiter.check(InMemoryCommsRateLimiter.MESSAGE_SEND, userId))
                .doesNotThrowAnyException();
        assertThatCode(() -> rateLimiter.check(InMemoryCommsRateLimiter.MESSAGE_SEND, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsOverLimit() {
        rateLimiter.check(InMemoryCommsRateLimiter.MESSAGE_SEND, userId);
        rateLimiter.check(InMemoryCommsRateLimiter.MESSAGE_SEND, userId);
        assertThatThrownBy(() -> rateLimiter.check(InMemoryCommsRateLimiter.MESSAGE_SEND, userId))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
