package com.university.lms.student.dto;

import com.university.lms.student.domain.ProgrammeEnrolmentEndReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EndProgrammeMembershipRequest(
        @NotNull(message = "is required") @PastOrPresent(message = "must not be in the future") LocalDate endedOn,
        @NotNull(message = "is required") ProgrammeEnrolmentEndReason endReason,
        @Size(max = 500, message = "must be at most 500 characters") String reason) {}
