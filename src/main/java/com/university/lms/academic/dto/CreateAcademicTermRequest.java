package com.university.lms.academic.dto;

import com.university.lms.academic.domain.TermType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a teaching period within an academic year.
 *
 * <p>The registration window is optional at creation. A term with no window is closed to
 * enrolment — registration must be opened deliberately rather than being available by default
 * because a field was left null.
 */
public record CreateAcademicTermRequest(
        @NotNull(message = "is required") UUID academicYearId,
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String name,
        @NotNull(message = "is required") TermType termType,
        @NotNull(message = "is required")
                @Min(value = 1, message = "must be at least 1")
                @Max(value = 12, message = "must be at most 12")
                Integer sequenceNumber,
        @NotNull(message = "is required") LocalDate startDate,
        @NotNull(message = "is required") LocalDate endDate,
        Instant registrationOpensAt,
        Instant registrationClosesAt) {}
