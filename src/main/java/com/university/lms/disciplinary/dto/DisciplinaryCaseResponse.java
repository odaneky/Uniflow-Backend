package com.university.lms.disciplinary.dto;

import com.university.lms.disciplinary.domain.DisciplinaryCase;
import com.university.lms.disciplinary.domain.DisciplinaryCategory;
import com.university.lms.disciplinary.domain.DisciplinaryCaseStatus;
import com.university.lms.disciplinary.domain.DisciplinaryOutcome;
import java.time.Instant;
import java.util.UUID;

public record DisciplinaryCaseResponse(
        UUID id,
        String caseNumber,
        UUID studentId,
        DisciplinaryCategory category,
        DisciplinaryCaseStatus status,
        String summary,
        UUID filedByUserId,
        String filedByName,
        UUID assignedOfficerUserId,
        String assignedOfficerName,
        DisciplinaryOutcome outcome,
        String outcomeReason,
        Instant filedAt,
        Instant resolvedAt) {

    public static DisciplinaryCaseResponse from(DisciplinaryCase entity, String filedByName, String assignedOfficerName) {
        return new DisciplinaryCaseResponse(
                entity.getId(),
                entity.getCaseNumber(),
                entity.getStudentId(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getSummary(),
                entity.getFiledByUserId(),
                filedByName,
                entity.getAssignedOfficerUserId(),
                assignedOfficerName,
                entity.getOutcome(),
                entity.getOutcomeReason(),
                entity.getFiledAt(),
                entity.getResolvedAt());
    }
}
