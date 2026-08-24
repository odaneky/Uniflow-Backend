package com.university.lms.administration.dto;

import com.university.lms.administration.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

/** One append-only trail row, as an operator sees it. */
public record AuditEventResponse(
        UUID id,
        UUID actorUserId,
        String actorLabel,
        String action,
        String entityType,
        UUID entityId,
        Instant occurredAt,
        String details,
        String reason,
        String beforeValue,
        String afterValue,
        String sourceIp,
        String correlationId) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorUserId(),
                event.getActorLabel(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getOccurredAt(),
                event.getDetails(),
                event.getReason(),
                event.getBeforeValue(),
                event.getAfterValue(),
                event.getSourceIp(),
                event.getCorrelationId());
    }
}
