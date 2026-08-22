package com.university.lms.academic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

/** Opens the add/drop period and publishes the tuition due date for the term. */
public record AddDropWindowRequest(
        @NotNull(message = "is required") Instant opensAt,
        @NotNull(message = "is required") Instant closesAt,
        LocalDate tuitionDueOn) {}
