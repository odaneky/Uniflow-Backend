package com.university.lms.curriculum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A registrar-approved substitution: passing {@link #substituteCourseId} satisfies a requirement
 * that names {@link #requiredCourseId}, for this student only. Consulted by the prerequisite check
 * ({@code CurriculumCatalog.hasPassed}) and by degree progress
 * ({@code CurriculumService.progressOf}) — both treat the required course as satisfied once the
 * student has passed (or transferred in) the substitute.
 *
 * <p>No mutator: a substitution is either approved or it does not exist. Revoking one is a delete,
 * not an edit — there is nothing partial about "the registrar changed their mind."
 */
@Entity
@Table(
        name = "course_substitutions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_course_substitutions_student_required",
                        columnNames = {"student_id", "required_course_id"}),
        indexes = @Index(name = "idx_course_substitutions_student", columnList = "student_id"))
@Getter
public class CourseSubstitution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "required_course_id", nullable = false)
    private UUID requiredCourseId;

    @Column(name = "substitute_course_id", nullable = false)
    private UUID substituteCourseId;

    /** Cross-module reference into request — the approved COURSE_SUBSTITUTION request. */
    @Column(name = "service_request_id", nullable = false)
    private UUID serviceRequestId;

    /** Cross-module reference into identity; who approved it. */
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    protected CourseSubstitution() {
        // for JPA
    }

    public CourseSubstitution(
            UUID studentId, UUID requiredCourseId, UUID substituteCourseId, UUID serviceRequestId, UUID approvedBy) {
        this.studentId = studentId;
        this.requiredCourseId = requiredCourseId;
        this.substituteCourseId = substituteCourseId;
        this.serviceRequestId = serviceRequestId;
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
    }
}
