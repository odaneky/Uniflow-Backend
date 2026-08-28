package com.university.lms.financialaid.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * E9: a named fund a student can be awarded from — {@code FinancialAidAward.scholarshipProgrammeId}
 * links a {@link AwardType#SCHOLARSHIP} award back to one of these. Distinct from PELL and
 * institutional aid, which are undifferentiated pots this codebase has never needed to name.
 */
@Entity
@Table(name = "scholarship_programmes")
@Getter
public class ScholarshipProgramme extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Null: institution-funded. Set: a named donor, foundation or company funds this programme. */
    @Column(name = "sponsor_name", length = 200)
    private String sponsorName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "default_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultAmount;

    @Column(name = "renewable", nullable = false)
    private boolean renewable;

    /** Null: no cap on how many times it can be renewed, so long as {@link #renewable} is true. */
    @Column(name = "max_renewals")
    private Integer maxRenewals;

    @Column(name = "eligibility_criteria", length = 2000)
    private String eligibilityCriteria;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ScholarshipProgramme() {}

    public ScholarshipProgramme(
            String name,
            String sponsorName,
            String description,
            BigDecimal defaultAmount,
            boolean renewable,
            Integer maxRenewals,
            String eligibilityCriteria) {
        replace(name, sponsorName, description, defaultAmount, renewable, maxRenewals, eligibilityCriteria, true);
    }

    public void replace(
            String name,
            String sponsorName,
            String description,
            BigDecimal defaultAmount,
            boolean renewable,
            Integer maxRenewals,
            String eligibilityCriteria,
            boolean active) {
        this.name = name;
        this.sponsorName = sponsorName;
        this.description = description;
        this.defaultAmount = defaultAmount;
        this.renewable = renewable;
        this.maxRenewals = maxRenewals;
        this.eligibilityCriteria = eligibilityCriteria;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }
}
