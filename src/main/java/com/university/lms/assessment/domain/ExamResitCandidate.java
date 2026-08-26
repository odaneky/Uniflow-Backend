package com.university.lms.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * G6: a student this resit or deferred sitting is actually for.
 *
 * <p>A sitting with no candidate rows is visible to its whole section, exactly as before this
 * existed; one with any rows is visible only to the students named here — see {@code
 * MyExamsService#ownTimetable} and {@code ExamScheduleService#notifyCandidates}.
 */
@Entity
@Table(name = "exam_resit_candidates")
@IdClass(ExamResitCandidate.ExamResitCandidateId.class)
@Getter
public class ExamResitCandidate {

    @Id
    @Column(name = "exam_sitting_id", nullable = false)
    private UUID examSittingId;

    @Id
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected ExamResitCandidate() {
        // for JPA
    }

    public ExamResitCandidate(UUID examSittingId, UUID studentId, UUID addedBy) {
        this.examSittingId = examSittingId;
        this.studentId = studentId;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
    }

    public record ExamResitCandidateId(UUID examSittingId, UUID studentId) implements Serializable {}
}
