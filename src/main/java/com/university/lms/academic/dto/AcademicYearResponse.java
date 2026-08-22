package com.university.lms.academic.dto;

import com.university.lms.academic.domain.AcademicYear;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicYearResponse(UUID id, String code, LocalDate startDate, LocalDate endDate) {

    public static AcademicYearResponse from(AcademicYear year) {
        return new AcademicYearResponse(year.getId(), year.getCode(), year.getStartDate(), year.getEndDate());
    }
}
