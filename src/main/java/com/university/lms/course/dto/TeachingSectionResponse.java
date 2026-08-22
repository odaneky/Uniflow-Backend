package com.university.lms.course.dto;

import java.util.UUID;

public record TeachingSectionResponse(
        UUID sectionId,
        UUID courseId,
        String courseCode,
        String title,
        String sectionCode,
        UUID academicTermId,
        UUID lecturerUserId,
        int enrolledCount,
        int capacity) {}
