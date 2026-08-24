package com.university.lms.student.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** A note an advisor leaves on an advisee's record — what was discussed, agreed, or should be followed up. */
@Entity
@Table(name = "advising_notes", indexes = @Index(name = "idx_advising_notes_student", columnList = "student_id,created_at"))
@Getter
public class AdvisingNote extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** Cross-module reference into identity — the advisor who wrote this note. */
    @Column(name = "advisor_user_id", nullable = false)
    private UUID advisorUserId;

    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    protected AdvisingNote() {}

    public AdvisingNote(UUID studentId, UUID advisorUserId, String note) {
        this.studentId = studentId;
        this.advisorUserId = advisorUserId;
        this.note = note;
    }
}
