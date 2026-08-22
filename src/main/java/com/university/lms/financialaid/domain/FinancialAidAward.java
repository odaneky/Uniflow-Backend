package com.university.lms.financialaid.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "financial_aid_awards",
        indexes = @Index(name = "idx_financial_aid_awards_student", columnList = "student_id, academic_term_id"))
@Getter
public class FinancialAidAward extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    @Enumerated(EnumType.STRING)
    @Column(name = "award_type", nullable = false, length = 20)
    private AwardType awardType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AwardStatus status = AwardStatus.OFFERED;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    protected FinancialAidAward() {}

    public FinancialAidAward(
            UUID studentId, UUID academicTermId, AwardType awardType, BigDecimal amount, AwardStatus status) {
        this.studentId = studentId;
        this.academicTermId = academicTermId;
        this.awardType = awardType;
        this.amount = amount;
        this.status = status == null ? AwardStatus.OFFERED : status;
    }

    public void accept() {
        if (status != AwardStatus.OFFERED) {
            throw new IllegalStateException("Only offered awards may be accepted");
        }
        status = AwardStatus.ACCEPTED;
    }

    public void decline() {
        if (status != AwardStatus.OFFERED) {
            throw new IllegalStateException("Only offered awards may be declined");
        }
        status = AwardStatus.DECLINED;
    }

    public void markDisbursed(Instant at) {
        if (status != AwardStatus.ACCEPTED) {
            throw new IllegalStateException("Only accepted awards may be disbursed");
        }
        status = AwardStatus.DISBURSED;
        disbursedAt = at;
    }
}
