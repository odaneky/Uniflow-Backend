package com.university.lms.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.OutboxWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Enqueues identity lifecycle events other modules react to without depending on identity's internals. */
@Component
public class IdentityOutboxPublisher {

    public static final String EVENT_ROLE_GRANTED = "IdentityRoleGranted";

    private static final Logger log = LoggerFactory.getLogger(IdentityOutboxPublisher.class);

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public IdentityOutboxPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * A5 groundwork: {@code staffing.dispatch.RoleGrantedAppointmentHandler} reacts to this by
     * giving the user a default institution-wide appointment, so org-scoped authorization has real
     * data to consult before any guard is narrowed to require it.
     */
    public void publishRoleGranted(UUID userId, String role) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId.toString());
            payload.put("role", role);
            outboxWriter.enqueue(
                    "USER",
                    userId,
                    EVENT_ROLE_GRANTED,
                    objectMapper.writeValueAsString(payload),
                    "RoleGranted:" + userId + ":" + role + ":" + UUID.randomUUID());
        } catch (Exception ex) {
            log.warn("Could not enqueue role-granted event for user {} role {}", userId, role, ex);
        }
    }
}
