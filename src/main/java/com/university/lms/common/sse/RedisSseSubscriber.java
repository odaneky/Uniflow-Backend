package com.university.lms.common.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/** Delivers Redis SSE events to local browser connections on this node. */
@Component
@ConditionalOnProperty(name = "lms.notifications.sse.provider", havingValue = "redis")
public class RedisSseSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisSseSubscriber.class);

    private final LocalSseEventPublisher localPublisher;
    private final ObjectMapper objectMapper;

    public RedisSseSubscriber(
            LocalSseEventPublisher localPublisher,
            ObjectMapper objectMapper,
            RedisMessageListenerContainer container) {
        this.localPublisher = localPublisher;
        this.objectMapper = objectMapper;
        container.addMessageListener(this, new PatternTopic(RedisSseEventPublisher.CHANNEL_PREFIX + "*"));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String userIdRaw = channel.substring(RedisSseEventPublisher.CHANNEL_PREFIX.length());
            UUID userId = UUID.fromString(userIdRaw);
            SseEvent event = objectMapper.readValue(message.getBody(), SseEvent.class);
            localPublisher.publish(userId, event);
        } catch (Exception ex) {
            log.debug("Ignored malformed Redis SSE message: {}", ex.getMessage());
        }
    }
}
