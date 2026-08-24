package com.university.lms.assessment.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A student's exam timetable, with enough context for the portal to decide how to present it.
 *
 * <p>{@code inExamPeriod} is answered by the server rather than inferred from the dates by each
 * client. Whether the university considers itself to be in exams is an institutional fact, not a
 * date comparison every caller should re-implement — and re-implementing it is how a web portal and
 * a mobile app end up disagreeing about whether exams have started.
 */
public record ExamTimetableResponse(
        boolean inExamPeriod,
        LocalDate examStartsOn,
        LocalDate examEndsOn,
        List<ExamSittingResponse> exams,
        List<ExamClash> clashes) {

    /**
     * Two of this student's own exams that overlap.
     *
     * <p>Reported rather than prevented. Nobody schedules a clash on purpose, but by the time a
     * student is looking at their timetable it already exists, and the only useful thing the portal
     * can do is name both papers so they can be quoted to the examinations office. Silently showing
     * two overlapping exams and leaving the student to notice is how somebody misses one.
     */
    public record ExamClash(java.util.UUID firstExamId, java.util.UUID secondExamId, String message) {}
}
