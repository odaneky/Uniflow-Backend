package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * A programme's per-credit tuition override, effective for one interval — see {@link
 * TuitionSchedule} for why this is a sequence of rows rather than one overwritten in place. At most
 * one row per programme is open ({@link #getEffectiveTo()} {@code null}) at a time.
 */
@Entity
@Table(name = "programme_tuition_rates")
@Getter
public class ProgrammeTuitionRate extends BaseEntity {

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "amount_per_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerCredit;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected ProgrammeTuitionRate() {}

    public ProgrammeTuitionRate(UUID programmeId, BigDecimal amountPerCredit, LocalDate effectiveFrom) {
        this.programmeId = programmeId;
        this.amountPerCredit = amountPerCredit;
        this.effectiveFrom = effectiveFrom;
    }

    /** Closes this interval — the row stops being the open, currently-effective one. */
    public void end(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
