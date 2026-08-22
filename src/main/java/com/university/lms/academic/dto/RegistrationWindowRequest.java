package com.university.lms.academic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Opens (or moves) the window during which students may register for a term's sections. */
public record RegistrationWindowRequest(
        @NotNull(message = "is required") Instant opensAt, @NotNull(message = "is required") Instant closesAt) {}
