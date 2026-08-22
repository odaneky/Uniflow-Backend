package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "fee_catalog")
@Getter
public class FeeCatalogItem extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private FeeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment", nullable = false, length = 30)
    private FeeAssessment assessment;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected FeeCatalogItem() {}

    public FeeCatalogItem(
            String name,
            String description,
            BigDecimal amount,
            FeeKind kind,
            FeeAssessment assessment,
            UUID courseId,
            UUID programmeId) {
        replace(name, description, amount, kind, assessment, courseId, programmeId, true);
    }

    public void replace(
            String name,
            String description,
            BigDecimal amount,
            FeeKind kind,
            FeeAssessment assessment,
            UUID courseId,
            UUID programmeId,
            boolean active) {
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.kind = kind;
        this.assessment = assessment;
        this.courseId = courseId;
        this.programmeId = programmeId;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }
}
