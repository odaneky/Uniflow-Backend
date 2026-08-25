package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

/**
 * The university's default per-credit tuition and campus fee, effective for one interval.
 *
 * <p>Was a single row overwritten in place — {@link #replace} rewrote the only copy, so "what was
 * the per-credit rate in Fall 2025" had no answer once a later change landed. Now a sequence of
 * rows, at most one of which is open ({@link #getEffectiveTo()} {@code null}) at a time; replacing
 * the rate closes the open row and opens a new one rather than mutating history.
 */
@Entity
@Table(name = "tuition_schedules")
@Getter
public class TuitionSchedule extends BaseEntity {

    public static final BigDecimal DEFAULT_PER_CREDIT = new BigDecimal("200.00");
    public static final BigDecimal DEFAULT_CAMPUS_FEE = new BigDecimal("350.00");

    @Column(name = "amount_per_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerCredit;

    @Column(name = "campus_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal campusFee;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected TuitionSchedule() {}

    public TuitionSchedule(BigDecimal amountPerCredit, BigDecimal campusFee, LocalDate effectiveFrom) {
        this.amountPerCredit = amountPerCredit;
        this.campusFee = campusFee;
        this.effectiveFrom = effectiveFrom;
    }

    /** Closes this interval — the row stops being the open, currently-effective one. */
    public void end(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
