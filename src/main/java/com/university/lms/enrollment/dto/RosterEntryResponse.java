package com.university.lms.enrollment.dto;

import java.util.UUID;

public record RosterEntryResponse(
        UUID studentId, UUID userId, String studentNumber, String fullName, String email, String status) {}
