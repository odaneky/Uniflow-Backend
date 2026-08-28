package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.ScholarshipProgramme;
import java.math.BigDecimal;
import java.util.UUID;

public record ScholarshipProgrammeResponse(
        UUID id,
        String name,
        String sponsorName,
        String description,
        BigDecimal defaultAmount,
        boolean renewable,
        Integer maxRenewals,
        String eligibilityCriteria,
        boolean active) {

    public static ScholarshipProgrammeResponse from(ScholarshipProgramme programme) {
        return new ScholarshipProgrammeResponse(
                programme.getId(),
                programme.getName(),
                programme.getSponsorName(),
                programme.getDescription(),
                programme.getDefaultAmount(),
                programme.isRenewable(),
                programme.getMaxRenewals(),
                programme.getEligibilityCriteria(),
                programme.isActive());
    }
}
