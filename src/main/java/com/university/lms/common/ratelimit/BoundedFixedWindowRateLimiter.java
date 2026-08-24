package com.university.lms.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixed-window counting, with the map size bounded.
 *
 * <p><b>Bounded on purpose.</b> A limiter keyed by client address is a memory-exhaustion vector if
 * it grows without limit: an attacker rotating source addresses would fill the map faster than the
 * flooding it is meant to stop could hurt. Entries whose window has elapsed are swept first; if the
 * ceiling is still exceeded, the oldest are evicted. Evicting resets someone's allowance, which is
 * the right way to fail — a rate limiter that kills the process protects nobody.
 *
 * <p>Fixed windows admit a burst of up to twice the limit across a boundary. That is a known and
 * accepted property here: this layer exists to stop automated floods, not to meter precisely, and a
 * sliding window costs materially more per request.
 *
 * <p>Process-local. Behind several instances each enforces its own share, so effective limits
 * multiply by the instance count — deliberate, given the architecture carries no shared cache. When
 * that stops being acceptable the store moves out; the interface does not have to.
 */
class BoundedFixedWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(BoundedFixedWindowRateLimiter.class);

    /** Outcome of an attempt. {@code retryAfter} is only meaningful when not {@code allowed}. */
    record Decision(boolean allowed, long retryAfterSeconds) {
        /** Named {@code permit} rather than {@code allowed} — the latter is the accessor. */
        static Decision permit() {
            return new Decision(true, 0);
        }
    }

    private final int maxTrackedKeys;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicBoolean sweeping = new AtomicBoolean(false);

    BoundedFixedWindowRateLimiter(int maxTrackedKeys) {
        this.maxTrackedKeys = Math.max(1024, maxTrackedKeys);
    }

    Decision tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        long windowSeconds = Math.max(1, window.getSeconds());

        Window state = windows.compute(key, (ignored, existing) -> existing == null
                        || existing.startedAt.plusSeconds(windowSeconds).isBefore(now)
                ? new Window(now, new AtomicInteger(0))
                : existing);

        int used = state.count.incrementAndGet();

        if (windows.size() > maxTrackedKeys) {
            sweep(now);
        }

        if (used <= limit) {
            return Decision.permit();
        }
        long elapsed = now.getEpochSecond() - state.startedAt.getEpochSecond();
        return new Decision(false, Math.max(1, windowSeconds - elapsed));
    }

    /**
     * One thread sweeps at a time. Several sweeping at once would each walk the whole map under
     * load — turning a defence against a flood into a second cost driven by the same flood.
     */
    private void sweep(Instant now) {
        if (!sweeping.compareAndSet(false, true)) {
            return;
        }
        try {
            // Widest plausible window; anything older cannot still be counting.
            windows.entrySet().removeIf(entry -> entry.getValue().startedAt.plusSeconds(3600).isBefore(now));

            int excess = windows.size() - maxTrackedKeys;
            if (excess > 0) {
                List<String> oldest = windows.entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getValue().startedAt))
                        .limit(excess)
                        .map(Map.Entry::getKey)
                        .toList();
                oldest.forEach(windows::remove);
                log.warn(
                        "Rate-limit table exceeded {} keys; evicted {} oldest entries. "
                                + "This usually means traffic from a very large number of distinct addresses.",
                        maxTrackedKeys,
                        oldest.size());
            }
        } finally {
            sweeping.set(false);
        }
    }

    /** Visible for tests and diagnostics. */
    int trackedKeys() {
        return windows.size();
    }

    private record Window(Instant startedAt, AtomicInteger count) {}
}
