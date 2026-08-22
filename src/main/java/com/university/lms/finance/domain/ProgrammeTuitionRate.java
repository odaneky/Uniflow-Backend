package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "programme_tuition_rates",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_programme_tuition_rates_programme", columnNames = "programme_id"))
@Getter
public class ProgrammeTuitionRate extends BaseEntity {

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "amount_per_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerCredit;

    protected ProgrammeTuitionRate() {}

    public ProgrammeTuitionRate(UUID programmeId, BigDecimal amountPerCredit) {
        this.programmeId = programmeId;
        this.amountPerCredit = amountPerCredit;
    }

    public void replace(BigDecimal amountPerCredit) {
        this.amountPerCredit = amountPerCredit;
    }
}
