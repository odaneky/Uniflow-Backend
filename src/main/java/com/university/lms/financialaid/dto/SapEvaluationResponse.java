package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.SapEvaluation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SapEvaluationResponse(
        UUID id,
        UUID studentId,
        UUID academicTermId,
        BigDecimal gpa,
        BigDecimal completionRate,
        boolean meetsSap,
        Instant evaluatedAt) {

    public static SapEvaluationResponse from(SapEvaluation evaluation) {
        return new SapEvaluationResponse(
                evaluation.getId(),
                evaluation.getStudentId(),
                evaluation.getAcademicTermId(),
                evaluation.getGpa(),
                evaluation.getCompletionRate(),
                evaluation.isMeetsSap(),
                evaluation.getEvaluatedAt());
    }
}
