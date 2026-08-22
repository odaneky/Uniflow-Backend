package com.university.lms.enrollment.dto;

import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

/** Representation of a registration record. */
public record EnrollmentResponse(
        UUID id,
        UUID studentId,
        UUID courseSectionId,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant endedAt,
        int attemptNumber) {

    public static EnrollmentResponse from(Enrollment enrolment) {
        return new EnrollmentResponse(
                enrolment.getId(),
                enrolment.getStudentId(),
                enrolment.getCourseSectionId(),
                enrolment.getStatus(),
                enrolment.getEnrolledAt(),
                enrolment.getEndedAt(),
                enrolment.getAttemptNumber());
    }
}
