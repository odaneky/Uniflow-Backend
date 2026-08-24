package com.university.lms.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoundedFixedWindowRateLimiterTest {

    @Test
    @DisplayName("permits exactly the limit, then refuses")
    void permitsUpToTheLimit() {
        BoundedFixedWindowRateLimiter limiter = new BoundedFixedWindowRateLimiter(1024);
        for (int i = 1; i <= 5; i++) {
            assertThat(limiter.tryAcquire("k", 5, Duration.ofMinutes(1)).allowed())
                    .as("request %d of 5", i)
                    .isTrue();
        }
        assertThat(limiter.tryAcquire("k", 5, Duration.ofMinutes(1)).allowed()).isFalse();
    }

    @Test
    @DisplayName("a refusal says how long to wait, and never zero")
    void refusalCarriesRetryAfter() {
        BoundedFixedWindowRateLimiter limiter = new BoundedFixedWindowRateLimiter(1024);
        limiter.tryAcquire("k", 1, Duration.ofMinutes(5));
        var decision = limiter.tryAcquire("k", 1, Duration.ofMinutes(5));

        assertThat(decision.allowed()).isFalse();
        // Zero would invite an immediate retry, turning Retry-After into a busy-wait instruction.
        assertThat(decision.retryAfterSeconds()).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("keys are independent — one client cannot exhaust another's allowance")
    void keysAreIndependent() {
        BoundedFixedWindowRateLimiter limiter = new BoundedFixedWindowRateLimiter(1024);
        IntStream.range(0, 5).forEach(i -> limiter.tryAcquire("noisy", 5, Duration.ofMinutes(1)));

        assertThat(limiter.tryAcquire("noisy", 5, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("quiet", 5, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    /**
     * The limiter must not become the vector. An attacker rotating source addresses would otherwise
     * grow this map until the process dies — a worse outcome than the flooding it prevents.
     */
    @Test
    @DisplayName("the tracking table stays bounded under a flood of distinct clients")
    void tableStaysBounded() {
        BoundedFixedWindowRateLimiter limiter = new BoundedFixedWindowRateLimiter(1024);
        for (int i = 0; i < 20_000; i++) {
            limiter.tryAcquire("flood:" + i, 10, Duration.ofMinutes(1));
        }
        assertThat(limiter.trackedKeys())
                .as("must be capped, not proportional to the number of distinct clients seen")
                .isLessThanOrEqualTo(2048);
    }

    @Test
    @DisplayName("counting is correct under concurrent access")
    void countsCorrectlyUnderConcurrency() throws Exception {
        BoundedFixedWindowRateLimiter limiter = new BoundedFixedWindowRateLimiter(1024);
        int limit = 100;
        int attempts = 400;
        AtomicInteger allowed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = IntStream.range(0, attempts)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        if (limiter.tryAcquire("shared", limit, Duration.ofMinutes(1)).allowed()) {
                            allowed.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();
            pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(allowed.get())
                .as("a counter that races would let more than the limit through")
                .isEqualTo(limit);
    }
}
