package com.university.lms.administration.dto;

import com.university.lms.administration.domain.RecordAccessEvent;
import java.time.Instant;
import java.util.UUID;

public record RecordAccessEventResponse(
        UUID id,
        UUID actorUserId,
        String actorLabel,
        UUID studentId,
        String recordType,
        String action,
        String details,
        Instant accessedAt) {

    public static RecordAccessEventResponse from(RecordAccessEvent event) {
        return new RecordAccessEventResponse(
                event.getId(),
                event.getActorUserId(),
                event.getActorLabel(),
                event.getStudentId(),
                event.getRecordType(),
                event.getAction(),
                event.getDetails(),
                event.getAccessedAt());
    }
}
