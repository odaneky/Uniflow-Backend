package com.university.lms.disciplinary.dto;

import com.university.lms.disciplinary.domain.DisciplinaryCaseNote;
import java.time.Instant;
import java.util.UUID;

public record DisciplinaryCaseNoteResponse(
        UUID id, UUID caseId, UUID authorUserId, String authorName, String note, Instant createdAt) {

    public static DisciplinaryCaseNoteResponse from(DisciplinaryCaseNote entity, String authorName) {
        return new DisciplinaryCaseNoteResponse(
                entity.getId(), entity.getCaseId(), entity.getAuthorUserId(), authorName, entity.getNote(), entity.getCreatedAt());
    }
}
