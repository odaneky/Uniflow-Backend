package com.university.lms.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Null fields mean inherit the institution default. */
public record ReplaceProgrammeCreditLoadRequest(
        @Min(1) @Max(40) Integer minSemesterCredits, @Min(1) @Max(40) Integer maxSemesterCredits) {}
