package com.university.lms.finance.dto;

import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeCatalogItem;
import com.university.lms.finance.domain.FeeKind;
import java.math.BigDecimal;
import java.util.UUID;

public record FeeResponse(
        UUID id,
        String name,
        String description,
        BigDecimal amount,
        FeeKind kind,
        FeeAssessment assessment,
        UUID courseId,
        String courseCode,
        UUID programmeId,
        String programmeCode,
        boolean active) {

    public static FeeResponse from(FeeCatalogItem fee, String courseCode, String programmeCode) {
        return new FeeResponse(
                fee.getId(),
                fee.getName(),
                fee.getDescription(),
                fee.getAmount(),
                fee.getKind(),
                fee.getAssessment(),
                fee.getCourseId(),
                courseCode,
                fee.getProgrammeId(),
                programmeCode,
                fee.isActive());
    }
}
