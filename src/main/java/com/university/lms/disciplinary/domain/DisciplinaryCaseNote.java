package com.university.lms.disciplinary.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** An entry in a case's history — append-only, the same shape {@code AdvisingNote} uses. */
@Entity
@Table(name = "disciplinary_case_notes", indexes = @Index(name = "idx_disciplinary_case_notes_case", columnList = "case_id,created_at"))
@Getter
public class DisciplinaryCaseNote extends BaseEntity {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    protected DisciplinaryCaseNote() {}

    public DisciplinaryCaseNote(UUID caseId, UUID authorUserId, String note) {
        this.caseId = caseId;
        this.authorUserId = authorUserId;
        this.note = note;
    }
}
