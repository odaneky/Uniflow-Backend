package com.university.lms.financialaid.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "sap_evaluations",
        indexes =
                @Index(name = "idx_sap_evaluations_student", columnList = "student_id, academic_term_id, evaluated_at"))
@Getter
public class SapEvaluation extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    @Column(name = "gpa", precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(name = "completion_rate", precision = 5, scale = 4)
    private BigDecimal completionRate;

    @Column(name = "meets_sap", nullable = false)
    private boolean meetsSap;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected SapEvaluation() {}

    public SapEvaluation(
            UUID studentId,
            UUID academicTermId,
            BigDecimal gpa,
            BigDecimal completionRate,
            boolean meetsSap,
            Instant evaluatedAt) {
        this.studentId = studentId;
        this.academicTermId = academicTermId;
        this.gpa = gpa;
        this.completionRate = completionRate;
        this.meetsSap = meetsSap;
        this.evaluatedAt = evaluatedAt;
    }
}
