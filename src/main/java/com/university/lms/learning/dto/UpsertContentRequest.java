package com.university.lms.learning.dto;

import jakarta.validation.constraints.Size;

/** Upserts the overview for a section's content tree. Creates the root row if it does not exist. */
public record UpsertContentRequest(
        @Size(max = 4000, message = "must be at most 4000 characters") String overview) {}
