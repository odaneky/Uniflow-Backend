package com.university.lms.student.dto;

import java.util.UUID;

/** Staff member who can be assigned as a student's academic advisor. */
public record AdvisorCandidateResponse(UUID userId, String fullName, String email, String roleLabel) {}
