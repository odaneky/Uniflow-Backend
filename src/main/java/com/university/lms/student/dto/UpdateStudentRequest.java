package com.university.lms.student.dto;

import com.university.lms.student.domain.StudentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update of a student record. Every field is optional; a null means "leave unchanged"
 * rather than "clear", which is why this is a PATCH rather than a PUT.
 *
 * <p>{@code clearAdvisor} is the explicit way to remove an assigned advisor. {@code advisorUserId}
 * assigns (or reassigns) when non-null.
 */
public record UpdateStudentRequest(
        UUID programmeId,
        StudentStatus status,
        LocalDate expectedGraduationDate,
        UUID advisorUserId,
        Boolean clearAdvisor,
        String advisorOfficeHours) {}
