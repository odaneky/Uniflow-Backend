package com.university.lms.student.dto;

import com.university.lms.student.domain.StudentStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update of a student record. Every field is optional; a null means "leave unchanged"
 * rather than "clear", which is why this is a PATCH rather than a PUT.
 *
 * <p>{@code clearAdvisor} is the explicit way to remove an assigned advisor. {@code advisorUserId}
 * assigns (or reassigns) when non-null. Office hours are posted by the assigned advisor via
 * {@code PATCH /me/advisor/office-hours}; {@code advisorOfficeHours} is ignored here.
 * {@code contact} applies demographic/contact corrections directly when present.
 *
 * <p>{@code reason} is required whenever {@code status} names a status transition (not required
 * when omitted, or when it repeats the student's current status) — {@code StudentService} enforces
 * this so every academic-standing change carries a stated reason in the audit trail.
 */
public record UpdateStudentRequest(
        UUID programmeId,
        StudentStatus status,
        LocalDate expectedGraduationDate,
        UUID advisorUserId,
        Boolean clearAdvisor,
        String advisorOfficeHours,
        UpdateOwnProfileRequest contact,
        @Size(max = 1000, message = "must be at most 1000 characters") String reason) {}
