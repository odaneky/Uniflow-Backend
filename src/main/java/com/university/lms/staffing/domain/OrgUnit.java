package com.university.lms.staffing.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * A node in the institution's organizational tree — self-referencing rather than a fixed
 * Faculty/Department pair, because a registrar's office, a bursar's office or a financial aid
 * office is a real unit staff are appointed to, and none of those is a "Department" in the sense
 * {@code academic.domain.Department} already means. That table is untouched by this one; the two
 * exist in parallel until something needs them unified.
 *
 * <p>{@link StaffAppointment} is what actually scopes a staff member to a unit — this entity is
 * just the tree they are scoped into.
 */
@Entity
@Table(
        name = "org_units",
        uniqueConstraints = @UniqueConstraint(name = "uk_org_units_code", columnNames = "code"),
        indexes = @Index(name = "idx_org_units_parent", columnList = "parent_org_unit_id"))
@Getter
public class OrgUnit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_org_unit_id", foreignKey = @ForeignKey(name = "fk_org_units_parent"))
    private OrgUnit parent;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 30)
    private OrgUnitType unitType;

    protected OrgUnit() {
        // for JPA
    }

    public OrgUnit(OrgUnit parent, String code, String name, OrgUnitType unitType) {
        this.parent = parent;
        this.code = code;
        this.name = name;
        this.unitType = unitType;
    }

    public void rename(String name) {
        this.name = name;
    }
}
