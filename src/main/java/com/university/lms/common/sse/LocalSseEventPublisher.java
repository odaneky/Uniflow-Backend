package com.university.lms.common.sse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-process SSE registry. Sufficient for single-node deployments; swap for Redis-backed
 * implementation when horizontal SSE fan-out is required.
 */
@Component
public class LocalSseEventPublisher implements SseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalSseEventPublisher.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        subscribers.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            remove(userId, emitter);
            log.debug("SSE connect handshake failed for user {}: {}", userId, ex.getMessage());
        }
        return emitter;
    }

    @Override
    public void publish(UUID userId, SseEvent event) {
        List<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.id())
                        .name(event.eventType())
                        .data(event.data(), MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                remove(userId, emitter);
                log.debug("SSE publish failed for user {}: {}", userId, ex.getMessage());
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(userId, emitters);
        }
    }
}
