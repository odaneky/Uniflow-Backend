package com.university.lms.communication.policy;

import com.university.lms.common.exception.RateLimitExceededException;
import com.university.lms.common.telemetry.CommsMetrics;
import com.university.lms.communication.api.CommsRateLimiter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Process-local fixed-window rate limiter. Sufficient for single-node deployments; swap for a shared
 * store when horizontal rate limiting is required.
 */
@Component
public class InMemoryCommsRateLimiter implements CommsRateLimiter {

    public static final String MESSAGE_SEND = "message_send";
    public static final String CONVERSATION_CREATE = "conversation_create";
    public static final String FORUM_POST = "forum_post";
    public static final String FORUM_TOPIC = "forum_topic";

    private final CommsMetrics commsMetrics;
    private final Map<String, Integer> limits;
    private final Map<String, Long> windowSeconds;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryCommsRateLimiter(
            CommsMetrics commsMetrics,
            @Value("${lms.comms.rate-limit.message-send.limit:30}") int messageSendLimit,
            @Value("${lms.comms.rate-limit.message-send.window-seconds:60}") long messageSendWindow,
            @Value("${lms.comms.rate-limit.conversation-create.limit:10}") int conversationCreateLimit,
            @Value("${lms.comms.rate-limit.conversation-create.window-seconds:3600}") long conversationCreateWindow,
            @Value("${lms.comms.rate-limit.forum-post.limit:20}") int forumPostLimit,
            @Value("${lms.comms.rate-limit.forum-post.window-seconds:3600}") long forumPostWindow,
            @Value("${lms.comms.rate-limit.forum-topic.limit:10}") int forumTopicLimit,
            @Value("${lms.comms.rate-limit.forum-topic.window-seconds:3600}") long forumTopicWindow) {
        this.commsMetrics = commsMetrics;
        this.limits = Map.of(
                MESSAGE_SEND, messageSendLimit,
                CONVERSATION_CREATE, conversationCreateLimit,
                FORUM_POST, forumPostLimit,
                FORUM_TOPIC, forumTopicLimit);
        this.windowSeconds = Map.of(
                MESSAGE_SEND, messageSendWindow,
                CONVERSATION_CREATE, conversationCreateWindow,
                FORUM_POST, forumPostWindow,
                FORUM_TOPIC, forumTopicWindow);
    }

    @Override
    public void check(String bucket, UUID userId) {
        int limit = limits.getOrDefault(bucket, 60);
        long window = windowSeconds.getOrDefault(bucket, 60L);
        String key = bucket + ":" + userId;
        Instant now = Instant.now();
        Window state = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.plusSeconds(window).isBefore(now)) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        int count = state.count.incrementAndGet();
        if (count > limit) {
            commsMetrics.rateLimitHit(bucket);
            long retryAfter = Math.max(
                    1,
                    window - (now.getEpochSecond() - state.windowStart.getEpochSecond()));
            throw new RateLimitExceededException(retryAfter);
        }
    }

    private record Window(Instant windowStart, AtomicInteger count) {}
}
