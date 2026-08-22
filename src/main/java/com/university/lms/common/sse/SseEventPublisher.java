package com.university.lms.common.sse;

import java.util.UUID;

/** Publishes live events to currently connected clients for a user. */
public interface SseEventPublisher {

    void publish(UUID userId, SseEvent event);
}
