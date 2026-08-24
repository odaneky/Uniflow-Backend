package com.university.lms.student.dto;

import com.university.lms.student.domain.ProgrammeEnrolmentKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.util.UUID;

/** Adds a minor, specialisation, or second major alongside a student's existing programme membership. */
public record AddProgrammeMembershipRequest(
        @NotNull(message = "is required") UUID programmeId,
        @NotNull(message = "is required") ProgrammeEnrolmentKind kind,
        @NotNull(message = "is required") @PastOrPresent(message = "must not be in the future") LocalDate startedOn) {}
