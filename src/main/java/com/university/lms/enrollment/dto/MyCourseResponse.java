package com.university.lms.enrollment.dto;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A course the caller is taking, joined with enough catalog detail to be useful on its own.
 *
 * <p>Composed in the service from the enrolment and the course module's published contract, rather
 * than by a database join across module tables — the boundary is what keeps either side free to
 * change its schema.
 */
public record MyCourseResponse(
        UUID enrollmentId,
        UUID courseSectionId,
        UUID courseId,
        String courseCode,
        String title,
        int credits,
        String sectionCode,
        UUID academicTermId,
        EnrollmentStatus status,
        Instant enrolledAt,
        List<CourseCatalog.Meeting> meetings) {}
