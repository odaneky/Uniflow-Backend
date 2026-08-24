package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.ExamSitting;
import java.time.Instant;
import java.util.UUID;

/**
 * One exam as a student sees it.
 *
 * <p>Carries the course code and title alongside the sitting, because an exam timetable is unusable
 * without them — "Hall A, 09:00" tells a student nothing about which paper to revise for.
 */
public record ExamSittingResponse(
        UUID id,
        UUID courseSectionId,
        String courseCode,
        String courseTitle,
        String sectionCode,
        String title,
        Instant startsAt,
        Instant endsAt,
        int durationMinutes,
        String room,
        String seating,
        /**
         * Whether students can see it. Always true on the student's own timetable — they are never
         * sent drafts — and the distinction that matters on the examinations office screen.
         */
        boolean published,
        String status,
        String cancelledReason) {

    public static ExamSittingResponse from(
            ExamSitting sitting, String courseCode, String courseTitle, String sectionCode) {
        return new ExamSittingResponse(
                sitting.getId(),
                sitting.getCourseSectionId(),
                courseCode,
                courseTitle,
                sectionCode,
                sitting.getTitle(),
                sitting.getStartsAt(),
                sitting.endsAt(),
                sitting.getDurationMinutes(),
                sitting.getRoom(),
                sitting.getSeating(),
                sitting.isPublished(),
                sitting.getStatus().name(),
                sitting.getCancelledReason());
    }
}
