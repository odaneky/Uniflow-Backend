package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.AwardStatus;
import com.university.lms.financialaid.domain.AwardType;
import com.university.lms.financialaid.domain.FinancialAidAward;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialAidAwardResponse(
        UUID id,
        UUID studentId,
        UUID academicTermId,
        AwardType awardType,
        BigDecimal amount,
        AwardStatus status,
        Instant disbursedAt,
        Instant createdAt,
        UUID scholarshipProgrammeId,
        UUID renewedFromAwardId) {

    public static FinancialAidAwardResponse from(FinancialAidAward award) {
        return new FinancialAidAwardResponse(
                award.getId(),
                award.getStudentId(),
                award.getAcademicTermId(),
                award.getAwardType(),
                award.getAmount(),
                award.getStatus(),
                award.getDisbursedAt(),
                award.getCreatedAt(),
                award.getScholarshipProgrammeId(),
                award.getRenewedFromAwardId());
    }
}
