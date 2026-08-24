package com.university.lms.academic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The examination period of a term.
 *
 * <p>Both ends required. A half-open window is ambiguous exactly when it matters — a student asking
 * whether exams have started needs a yes or a no, not "started, end unknown".
 */
public record ExamWindowRequest(
        @NotNull(message = "is required") LocalDate startsOn,
        @NotNull(message = "is required") LocalDate endsOn) {}
