package com.university.lms.academic.dto;

import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.academic.domain.Programme;
import java.util.UUID;

public record ProgrammeResponse(
        UUID id,
        UUID departmentId,
        String code,
        String name,
        String degreeAward,
        int totalCredits,
        int durationYears,
        boolean active,
        Integer minSemesterCredits,
        Integer maxSemesterCredits,
        int effectiveMinSemesterCredits,
        int effectiveMaxSemesterCredits) {

    public static ProgrammeResponse from(Programme programme, CreditLoad load) {
        return new ProgrammeResponse(
                programme.getId(),
                programme.getDepartment().getId(),
                programme.getCode(),
                programme.getName(),
                programme.getDegreeAward(),
                programme.getTotalCredits(),
                programme.getDurationYears(),
                programme.isActive(),
                programme.getMinSemesterCredits(),
                programme.getMaxSemesterCredits(),
                load.minSemesterCredits(),
                load.maxSemesterCredits());
    }
}
