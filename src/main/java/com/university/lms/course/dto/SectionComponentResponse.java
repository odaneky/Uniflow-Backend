package com.university.lms.course.dto;

import com.university.lms.course.domain.CourseComponent;
import java.util.UUID;

/** One teaching activity inside an occurrence, with its own seats and teacher. */
public record SectionComponentResponse(
        UUID id,
        CourseComponent component,
        int capacity,
        UUID lecturerUserId,
        String lecturerName) {}
