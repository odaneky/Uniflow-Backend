package com.university.lms.financialaid.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "isir_snapshots",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_isir_snapshots_student_year", columnNames = {"student_id", "aid_year"}),
        indexes = @Index(name = "idx_isir_snapshots_student", columnList = "student_id, aid_year"))
@Getter
public class IsirSnapshot extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "aid_year", nullable = false, length = 9)
    private String aidYear;

    @Column(name = "efc", precision = 12, scale = 2)
    private BigDecimal efc;

    @Column(name = "pell_eligible", nullable = false)
    private boolean pellEligible;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected IsirSnapshot() {}

    public IsirSnapshot(
            UUID studentId, String aidYear, BigDecimal efc, boolean pellEligible, String rawJson, Instant importedAt) {
        this.studentId = studentId;
        this.aidYear = aidYear;
        this.efc = efc;
        this.pellEligible = pellEligible;
        this.rawJson = rawJson;
        this.importedAt = importedAt;
    }

    public void replace(BigDecimal efc, boolean pellEligible, String rawJson, Instant importedAt) {
        this.efc = efc;
        this.pellEligible = pellEligible;
        this.rawJson = rawJson;
        this.importedAt = importedAt;
    }
}
