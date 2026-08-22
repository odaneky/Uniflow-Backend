package com.university.lms.course.dto;

import java.util.UUID;

/** A lecturer account, including those not yet assigned to an occurrence. */
public record AssignedLecturerResponse(UUID userId, String name, String email, int sections) {}
