package com.university.lms.enrollment.dto;

import java.util.UUID;

public record SectionCancellationResponse(UUID courseSectionId, int studentsAffected, int seatsReleased) {}
