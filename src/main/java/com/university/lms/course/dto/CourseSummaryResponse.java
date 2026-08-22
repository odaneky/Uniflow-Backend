package com.university.lms.course.dto;

import com.university.lms.course.domain.Course;
import com.university.lms.course.domain.CourseStatus;
import java.util.UUID;

/** Compact representation for catalog browsing; omits the description, which dominates payload size. */
public record CourseSummaryResponse(
        UUID id, String courseCode, String title, int credits, int level, CourseStatus status) {

    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.getId(),
                course.getCourseCode(),
                course.getTitle(),
                course.getCredits(),
                course.getLevel(),
                course.getStatus());
    }
}
