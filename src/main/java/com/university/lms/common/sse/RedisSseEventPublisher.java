package com.university.lms.common.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Publishes SSE events to Redis pub/sub for multi-node fan-out. Each instance also runs {@link
 * RedisSseSubscriber} to deliver remote events to local SSE connections.
 */
@Component
@Primary
@ConditionalOnProperty(name = "lms.notifications.sse.provider", havingValue = "redis")
public class RedisSseEventPublisher implements SseEventPublisher {

    static final String CHANNEL_PREFIX = "uniflow:sse:";

    private static final Logger log = LoggerFactory.getLogger(RedisSseEventPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSseEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(UUID userId, SseEvent event) {
        try {
            redis.convertAndSend(CHANNEL_PREFIX + userId, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize SSE event for user {}: {}", userId, ex.getMessage());
        }
    }
}
