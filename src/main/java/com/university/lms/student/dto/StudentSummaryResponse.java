package com.university.lms.student.dto;

import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentStatus;
import java.util.UUID;

/**
 * Compact representation for list endpoints. Separate from {@code StudentResponse} so that paging
 * through thousands of students does not serialise fields no list view displays.
 *
 * <p>{@code fullName} and {@code email} come from identity, not from this table — a student record
 * does not duplicate them.
 */
public record StudentSummaryResponse(
        UUID id,
        String studentNumber,
        UUID programmeId,
        StudentStatus status,
        String fullName,
        String email) {

    public static StudentSummaryResponse from(Student student, String fullName, String email) {
        return new StudentSummaryResponse(
                student.getId(),
                student.getStudentNumber(),
                student.getProgrammeId(),
                student.getStatus(),
                fullName,
                email);
    }
}
