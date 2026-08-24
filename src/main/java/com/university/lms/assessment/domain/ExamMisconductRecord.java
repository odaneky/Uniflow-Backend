package com.university.lms.assessment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** An examinations-office report that a candidate breached exam conduct rules during a sitting. */
@Entity
@Table(
        name = "exam_misconduct_records",
        indexes = @Index(name = "idx_exam_misconduct_sitting", columnList = "exam_sitting_id,created_at"))
@Getter
public class ExamMisconductRecord extends BaseEntity {

    @Column(name = "exam_sitting_id", nullable = false)
    private UUID examSittingId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    /** Cross-module reference into identity — the staff member who filed the report. */
    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    protected ExamMisconductRecord() {}

    public ExamMisconductRecord(UUID examSittingId, UUID studentId, String description, UUID reportedBy) {
        this.examSittingId = examSittingId;
        this.studentId = studentId;
        this.description = description;
        this.reportedBy = reportedBy;
    }
}
