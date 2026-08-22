package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "payment_installments",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_payment_installments_position", columnNames = {"plan_id", "position"}))
@Getter
public class PaymentInstallment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_installments_plan"))
    private PaymentPlan plan;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "cumulative_percent", nullable = false)
    private int cumulativePercent;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "week_of_term")
    private Integer weekOfTerm;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "places_hold", nullable = false)
    private boolean placesHold;

    @Column(name = "blocks_exams", nullable = false)
    private boolean blocksExams;

    protected PaymentInstallment() {}

    public PaymentInstallment(
            PaymentPlan plan,
            int position,
            String label,
            int cumulativePercent,
            Integer weekOfTerm,
            LocalDate dueOn,
            boolean placesHold,
            boolean blocksExams) {
        this.plan = plan;
        this.position = position;
        this.label = label;
        this.cumulativePercent = cumulativePercent;
        this.weekOfTerm = weekOfTerm;
        this.dueOn = dueOn;
        this.placesHold = placesHold;
        this.blocksExams = blocksExams;
    }
}
