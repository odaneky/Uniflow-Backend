package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;

/** The per-credit rate charged to students of one residency tier, absent a programme override. */
@Entity
@Table(
        name = "residency_tuition_rates",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_residency_tuition_rates_classification",
                        columnNames = "residency_classification"))
@Getter
public class ResidencyTuitionRate extends BaseEntity {

    @Column(name = "residency_classification", nullable = false, length = 30)
    private String residencyClassification;

    @Column(name = "amount_per_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerCredit;

    protected ResidencyTuitionRate() {}

    public ResidencyTuitionRate(String residencyClassification, BigDecimal amountPerCredit) {
        this.residencyClassification = residencyClassification;
        this.amountPerCredit = amountPerCredit;
    }

    public void replace(BigDecimal amountPerCredit) {
        this.amountPerCredit = amountPerCredit;
    }
}
