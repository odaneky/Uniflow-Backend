package com.university.lms.student.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A scheduled meeting between an advisor and their advisee. Advisor-initiated, the same direction
 * {@link AdvisingNote} already takes: an advisor books the slot, and the student sees it on their
 * own upcoming appointments.
 */
@Entity
@Table(
        name = "advising_appointments",
        indexes = {
            @Index(name = "idx_advising_appointments_student", columnList = "student_id,scheduled_at"),
            @Index(name = "idx_advising_appointments_advisor", columnList = "advisor_user_id,scheduled_at")
        })
@Getter
public class AdvisingAppointment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** Cross-module reference into identity — the advisor who booked this appointment. */
    @Column(name = "advisor_user_id", nullable = false)
    private UUID advisorUserId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    protected AdvisingAppointment() {}

    public AdvisingAppointment(UUID studentId, UUID advisorUserId, Instant scheduledAt, int durationMinutes, String note) {
        this.studentId = studentId;
        this.advisorUserId = advisorUserId;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
        this.note = note;
    }

    public void cancel(String reason) {
        this.cancelledAt = Instant.now();
        this.cancelledReason = reason;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
