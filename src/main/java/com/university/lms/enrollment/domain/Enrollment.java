package com.university.lms.enrollment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One student's registration in one course section.
 *
 * <p>Both references are cross-module identifiers (student, course) rather than associations, and
 * an active seat is unique — {@code uk_enrollments_student_section_active} (ENROLLED / PENDING /
 * WAITLISTED). A dropped row must not block the student from selecting the same section again.
 * The application check that precedes the index exists only to produce a better error message.
 */
@Entity
@Table(
        name = "enrollments",
        indexes = {
            @Index(name = "idx_enrollments_student", columnList = "student_id"),
            @Index(name = "idx_enrollments_section", columnList = "course_section_id"),
            @Index(name = "idx_enrollments_status", columnList = "status"),
            @Index(name = "idx_enrollments_checkout_batch", columnList = "student_id,checkout_batch_id")
        })
@Getter
public class Enrollment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Set when this row was created by student checkout; null for staff single-enrol. */
    @Column(name = "checkout_batch_id")
    private UUID checkoutBatchId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Enrollment() {
        // for JPA
    }

    public Enrollment(UUID studentId, UUID courseSectionId) {
        this(studentId, courseSectionId, EnrollmentStatus.ENROLLED);
    }

    public Enrollment(UUID studentId, UUID courseSectionId, EnrollmentStatus status) {
        this.studentId = studentId;
        this.courseSectionId = courseSectionId;
        if (status != null) {
            this.status = status;
        }
    }

    public Enrollment(UUID studentId, UUID courseSectionId, EnrollmentStatus status, UUID checkoutBatchId) {
        this(studentId, courseSectionId, status);
        this.checkoutBatchId = checkoutBatchId;
    }

    public boolean occupiesSeat() {
        return status.occupiesSeat();
    }

    /**
     * Moves to {@code target}, refusing any transition the lifecycle does not permit.
     *
     * @throws IllegalStateException if the transition is not allowed; callers translate this into
     *     a domain error rather than letting it escape as a 500
     */
    public void transitionTo(EnrollmentStatus target) {
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot move enrolment from " + status + " to " + target);
        }
        this.status = target;
        if (target.isTerminal()) {
            this.endedAt = Instant.now();
        }
    }
}
