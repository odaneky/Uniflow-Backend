package com.university.lms.student.dto;

import java.util.UUID;

/** One advisee on an advisor's list. */
public record AdviseeSummaryResponse(
        UUID studentId,
        String studentNumber,
        String fullName,
        String email,
        UUID programmeId,
        String status) {}
