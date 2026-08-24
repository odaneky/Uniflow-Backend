package com.university.lms.assessment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A scheduled exam: a hall, a clock time, a seat.
 *
 * <p>Distinct from {@link Assessment}, which is the academic artifact — weight, maximum score, who
 * set it. This is the logistics of sitting it, usually fixed centrally by an examinations office
 * well after the paper was written. The two are linked by {@code assessmentId} when a sitting is the
 * occasion on which a particular graded assessment is taken, and unlinked when it is not.
 *
 * <p>Cross-module references are plain ids rather than JPA associations, as everywhere else here —
 * {@code courseSectionId} points into the course module without binding the two object graphs.
 */
@Entity
@Table(
        name = "exam_sittings",
        indexes = {
            @Index(name = "idx_exam_sittings_section", columnList = "course_section_id, starts_at"),
            @Index(name = "idx_exam_sittings_room", columnList = "room, starts_at")
        })
@Getter
public class ExamSitting extends BaseEntity {

    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    /** Null when the sitting is not tied to a graded assessment record. */
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "room", nullable = false, length = 60)
    private String room;

    /**
     * Free text — "Rows 1–12", "Seats 40–78", "Alphabetical A–K". Universities describe seating in
     * prose, and a numeric range would be a model nobody could actually use.
     */
    @Column(name = "seating", length = 120)
    private String seating;

    /**
     * A draft timetable is worked on for weeks and is wrong for most of that time, so students see
     * nothing until the examinations office publishes it.
     */
    @Column(name = "published", nullable = false)
    private boolean published;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamSittingStatus status = ExamSittingStatus.SCHEDULED;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    protected ExamSitting() {
        // for JPA
    }

    public ExamSitting(
            UUID courseSectionId, String title, Instant startsAt, int durationMinutes, String room, String seating) {
        this.courseSectionId = courseSectionId;
        this.title = title;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.room = room;
        this.seating = seating;
    }

    public Instant endsAt() {
        return startsAt.plusSeconds(durationMinutes * 60L);
    }

    public void reschedule(Instant startsAt, int durationMinutes, String room, String seating) {
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.room = room;
        this.seating = seating;
    }

    public void linkAssessment(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void publish() {
        this.published = true;
    }

    /** Withdraws a published timetable — used when a sitting has to be moved after release. */
    public void unpublish() {
        this.published = false;
    }

    /**
     * Cancels the sitting without removing it.
     *
     * <p>It stops appearing on student timetables and stops holding its hall, but the record of it
     * having existed — and why it was withdrawn — remains.
     */
    public void cancel(String reason) {
        this.status = ExamSittingStatus.CANCELLED;
        this.cancelledReason = reason;
        this.published = false;
    }

    public boolean isCancelled() {
        return status == ExamSittingStatus.CANCELLED;
    }
}
