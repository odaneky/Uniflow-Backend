package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** One row: the university's default per-credit tuition and campus fee. */
@Entity
@Table(name = "tuition_schedules")
@Getter
public class TuitionSchedule extends BaseEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-000000000002");
    public static final BigDecimal DEFAULT_PER_CREDIT = new BigDecimal("200.00");
    public static final BigDecimal DEFAULT_CAMPUS_FEE = new BigDecimal("350.00");

    @Column(name = "amount_per_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerCredit;

    @Column(name = "campus_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal campusFee;

    protected TuitionSchedule() {}

    public TuitionSchedule(BigDecimal amountPerCredit, BigDecimal campusFee) {
        setId(SINGLETON_ID);
        replace(amountPerCredit, campusFee);
    }

    public void replace(BigDecimal amountPerCredit, BigDecimal campusFee) {
        this.amountPerCredit = amountPerCredit;
        this.campusFee = campusFee;
    }
}
