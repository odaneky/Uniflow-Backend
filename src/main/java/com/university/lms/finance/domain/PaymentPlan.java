package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "payment_plans",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_plans_term", columnNames = "academic_term_id"))
@Getter
public class PaymentPlan extends BaseEntity {

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PaymentInstallment> installments = new ArrayList<>();

    protected PaymentPlan() {}

    public PaymentPlan(UUID academicTermId) {
        this.academicTermId = academicTermId;
    }

    public void replaceInstallments(List<PaymentInstallment> next) {
        installments.clear();
        installments.addAll(next);
    }
}
